import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { errorMessage, useAsync } from '@/lib/useAsync';
import { useAuth } from '@/lib/auth';
import { formatDate, formatDateTime, formatMoney } from '@/lib/format';
import type {
  CashReconciliation,
  ReconciliationDecision,
  ReconciliationEvidence,
  ReconciliationReviewStatus,
  ReconciliationStatus,
} from '@/lib/types';
import { Badge, EmptyState, ErrorState, PageHeader } from '@/components/ui';
import {
  AlertIcon,
  CheckIcon,
  CoinsIcon,
  LockIcon,
  PaperclipIcon,
  RefreshIcon,
  ShieldIcon,
  UploadIcon,
  WalletIcon,
} from '@/components/Icons';

type Flash = { tone: 'success' | 'warning'; message: string };

const BUCKETS = [
  { key: 'cash', ledgerKey: 'cashMinor', label: 'accountability.cash', icon: WalletIcon },
  { key: 'airtel', ledgerKey: 'airtelMinor', label: 'accountability.airtel', icon: CoinsIcon },
  { key: 'orange', ledgerKey: 'orangeMinor', label: 'accountability.orange', icon: CoinsIcon },
  { key: 'mpesa', ledgerKey: 'mpesaMinor', label: 'accountability.mpesa', icon: CoinsIcon },
] as const;

const MAX_EVIDENCE = 5;
const MAX_EVIDENCE_BYTES = 5 * 1024 * 1024;
const EVIDENCE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];

function StatusBadge({ status }: { status: ReconciliationStatus }) {
  const { t } = useTranslation();
  return (
    <Badge tone={status === 'BALANCED' ? 'forest' : 'danger'}>
      {status === 'BALANCED' ? t('accountability.statusBalanced') : t('accountability.statusDiscrepancy')}
    </Badge>
  );
}

function ReviewBadge({ status }: { status: ReconciliationReviewStatus }) {
  const { t } = useTranslation();
  if (status === 'APPROVED') {
    return <Badge tone="forest">{t('accountability.reviewApproved')}</Badge>;
  }
  if (status === 'REJECTED') {
    return <Badge tone="danger">{t('accountability.reviewRejected')}</Badge>;
  }
  return <Badge tone="gold">{t('accountability.reviewPending')}</Badge>;
}

function parseMinor(value: string): number | null {
  const normalized = value.trim().replace(',', '.');
  if (normalized === '') {
    return 0;
  }
  if (!/^\d{1,15}(\.\d{1,2})?$/.test(normalized)) {
    return null;
  }
  const [whole, fraction = ''] = normalized.split('.');
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
}

function signedMoney(minor: number, language: string): string {
  return `${minor > 0 ? '+' : ''}${formatMoney(minor, language)}`;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(0)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function AccountabilityPage() {
  const { t, i18n } = useTranslation();
  const { hasRole } = useAuth();
  const { data, loading, error, reload } = useAsync(() => api.transparency(), []);
  const isLeader = hasRole('LEADER');
  const isAuditor = hasRole('AUDITOR');
  const auditorReports = useAsync(
    () => (isAuditor ? api.reconciliations(0, 50) : Promise.resolve(null)),
    [isAuditor],
  );
  const fileInput = useRef<HTMLInputElement>(null);

  const [form, setForm] = useState({
    businessDate: '',
    cash: '0',
    airtel: '0',
    orange: '0',
    mpesa: '0',
    notes: '',
  });
  const [evidenceFiles, setEvidenceFiles] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [flash, setFlash] = useState<Flash | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [reviewNotes, setReviewNotes] = useState<Record<string, string>>({});
  const [reviewing, setReviewing] = useState<string | null>(null);

  useEffect(() => {
    if (data && !form.businessDate) {
      setForm((current) => ({ ...current, businessDate: data.today }));
    }
  }, [data, form.businessDate]);

  if (loading) {
    return (
      <div>
        <div className="skeleton mb-6 h-16 w-72" />
        <div className="grid gap-4 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="skeleton h-28 rounded-3xl" />
          ))}
        </div>
        <div className="mt-4 skeleton h-72 rounded-3xl" />
      </div>
    );
  }

  if (error || !data) {
    return <ErrorState message={error ?? t('common.error')} onRetry={reload} />;
  }

  const parsed = {
    cash: parseMinor(form.cash),
    airtel: parseMinor(form.airtel),
    orange: parseMinor(form.orange),
    mpesa: parseMinor(form.mpesa),
  };
  const book = {
    cash: data.ledger.cashMinor,
    airtel: data.ledger.airtelMinor,
    orange: data.ledger.orangeMinor,
    mpesa: data.ledger.mpesaMinor,
  };
  const allParsed = Object.values(parsed).every((value) => value !== null);
  const formTotal = allParsed
    ? (parsed.cash ?? 0) + (parsed.airtel ?? 0) + (parsed.orange ?? 0) + (parsed.mpesa ?? 0)
    : null;
  const bookTotal = book.cash + book.airtel + book.orange + book.mpesa;

  const pendingCorrections = auditorReports.data?.content.filter((report) => report.reviewStatus === 'PENDING') ?? [];

  const addFiles = (files: FileList | null) => {
    if (!files || files.length === 0) {
      return;
    }
    setFormError(null);
    const accepted: File[] = [];
    for (const file of Array.from(files)) {
      if (!EVIDENCE_TYPES.includes(file.type)) {
        setFormError(t('accountability.evidenceType'));
        continue;
      }
      if (file.size > MAX_EVIDENCE_BYTES) {
        setFormError(t('accountability.evidenceTooLarge'));
        continue;
      }
      accepted.push(file);
    }
    setEvidenceFiles((current) => {
      const merged = [...current, ...accepted];
      if (merged.length > MAX_EVIDENCE) {
        setFormError(t('accountability.evidenceLimit', { count: MAX_EVIDENCE }));
        return merged.slice(0, MAX_EVIDENCE);
      }
      return merged;
    });
  };

  const removeFile = (index: number) => {
    setEvidenceFiles((current) => current.filter((_, itemIndex) => itemIndex !== index));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError(null);
    setFlash(null);
    if (!allParsed) {
      setFormError(t('accountability.invalidCount'));
      return;
    }
    const payload = {
      businessDate: form.businessDate,
      countedCash: form.cash.trim() === '' ? '0' : form.cash.trim(),
      countedAirtel: form.airtel.trim() === '' ? '0' : form.airtel.trim(),
      countedOrange: form.orange.trim() === '' ? '0' : form.orange.trim(),
      countedMpesa: form.mpesa.trim() === '' ? '0' : form.mpesa.trim(),
      notes: form.notes.trim() || null,
    };
    setSubmitting(true);
    try {
      const result = await api.submitReconciliation(payload);
      let failedUploads = 0;
      for (const file of evidenceFiles) {
        try {
          await api.uploadEvidence(result.id, file);
        } catch {
          failedUploads++;
        }
      }
      setEvidenceFiles([]);
      if (fileInput.current) {
        fileInput.current.value = '';
      }
      if (failedUploads > 0) {
        setFlash({
          tone: 'warning',
          message: t('accountability.evidenceUploadFailed', { count: failedUploads }),
        });
      } else {
        setFlash({
          tone: result.reviewStatus === 'PENDING' || result.status === 'DISCREPANCY' ? 'warning' : 'success',
          message:
            result.reviewStatus === 'PENDING'
              ? t('accountability.successPendingReview')
              : result.status === 'BALANCED'
                ? t('accountability.successBalanced')
                : t('accountability.successDiscrepancy'),
        });
      }
      reload();
      auditorReports.reload();
    } catch (submitError) {
      setFormError(errorMessage(submitError));
    } finally {
      setSubmitting(false);
    }
  };

  const handleReview = async (report: CashReconciliation, decision: ReconciliationDecision) => {
    const note = (reviewNotes[report.id] ?? '').trim();
    setFormError(null);
    setFlash(null);
    if (decision === 'REJECTED' && !note) {
      setFormError(t('accountability.rejectionNoteRequired'));
      return;
    }
    setReviewing(report.id);
    try {
      await api.reviewReconciliation(report.id, { decision, note: note || null });
      setReviewNotes((current) => ({ ...current, [report.id]: '' }));
      setFlash({
        tone: decision === 'APPROVED' ? 'success' : 'warning',
        message: decision === 'APPROVED' ? t('accountability.reviewApprovedFlash') : t('accountability.reviewRejectedFlash'),
      });
      reload();
      auditorReports.reload();
    } catch (reviewError) {
      setFormError(errorMessage(reviewError));
    } finally {
      setReviewing(null);
    }
  };

  const openEvidence = async (reportId: string, evidence: ReconciliationEvidence) => {
    try {
      const blob = await api.evidenceBlob(reportId, evidence.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.download = evidence.fileName;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (evidenceError) {
      setFlash({ tone: 'warning', message: errorMessage(evidenceError) });
    }
  };

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.accountability')}
        title={t('accountability.title')}
        subtitle={isLeader ? t('accountability.subtitle') : t('accountability.memberSubtitle')}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            {data.pendingReviewCount > 0 && <Badge tone="gold">{t('accountability.reviewPending')}</Badge>}
            <Badge tone={data.submittedToday ? 'forest' : 'gold'}>
              {data.submittedToday ? t('accountability.todayDone') : t('accountability.todayPending')}
            </Badge>
          </div>
        }
      />

      {flash && (
        <div
          className={`mb-5 flex items-start gap-2 rounded-2xl border px-4 py-3 text-xs font-medium ${
            flash.tone === 'success' ? 'border-forest-200 bg-forest-50 text-forest-800' : 'border-gold-200 bg-gold-50 text-gold-800'
          }`}
        >
          {flash.tone === 'success' ? (
            <CheckIcon size={16} className="mt-0.5 shrink-0 text-forest-600" />
          ) : (
            <AlertIcon size={16} className="mt-0.5 shrink-0 text-gold-700" />
          )}
          <span className="flex-1">{flash.message}</span>
          <button type="button" onClick={() => setFlash(null)} className="opacity-50 hover:opacity-100">
            ×
          </button>
        </div>
      )}

      {data.pendingReviewCount > 0 && (
        <div className="mb-4 flex items-start gap-2 rounded-2xl border border-gold-200 bg-gold-50/70 px-4 py-3 text-xs text-gold-800">
          <LockIcon size={15} className="mt-0.5 shrink-0" />
          <span>{t('accountability.pendingNotice', { count: data.pendingReviewCount })}</span>
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <Metric
          label={t('accountability.latest')}
          value={data.latestReconciliation ? formatDate(data.latestReconciliation.businessDate, i18n.language) : t('common.notAvailable')}
          hint={data.latestReconciliation ? t('accountability.revision', { revision: data.latestReconciliation.revision }) : t('accountability.noReports')}
          tone={data.latestReconciliation?.status}
        />
        <Metric label={t('accountability.reconciledDays')} value={String(data.reconciledDays)} hint={t('accountability.discrepancyDaysValue', { count: data.discrepancyDays })} />
        <Metric
          label={t('accountability.absoluteDifference')}
          value={formatMoney(data.absoluteDifferenceMinor, i18n.language)}
          hint={t('accountability.last30')}
          tone={data.absoluteDifferenceMinor === 0 ? 'BALANCED' : 'DISCREPANCY'}
        />
        <Metric label={t('accountability.reversals')} value={String(data.reversals)} hint={t('accountability.auditEventsValue', { count: data.auditEvents })} />
        <Metric label={t('accountability.discrepancyDays')} value={String(data.discrepancyDays)} hint={t('accountability.discrepancyDaysValue', { count: data.discrepancyDays })} />
      </section>

      {data.beforeLock !== false && (
        <section className="card mt-4 p-5 sm:p-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="font-display text-lg font-semibold text-forest-950">{t('accountability.lockTitle')}</h2>
              <p className="mt-1 max-w-2xl text-xs leading-relaxed text-forest-800/55">{t('accountability.lockBody')}</p>
            </div>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-parchment px-3 py-1 text-[0.65rem] font-semibold text-forest-700">
              <LockIcon size={12} /> {data.beforeLock ? t('accountability.lockNow') : t('accountability.lockGone')}
            </span>
          </div>

          <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {BUCKETS.map((bucket) => {
              const Icon = bucket.icon;
              const bookMinor = book[bucket.key];
              return (
                <div key={bucket.key} className="rounded-2xl border border-stone-200 bg-white/70 p-4">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[0.68rem] font-bold uppercase tracking-[0.1em] text-forest-800/55">{t(bucket.label)}</span>
                    <span className="grid h-7 w-7 place-items-center rounded-lg bg-forest-50 text-forest-600">
                      <Icon size={14} />
                    </span>
                  </div>
                  <div className="mt-2 font-display text-xl font-semibold tabular-nums text-forest-950">{formatMoney(bookMinor, i18n.language)}</div>
                  <div className="mt-1 text-[0.65rem] text-forest-800/40">{t('accountability.book')}</div>
                </div>
              );
            })}
          </div>

          <div className="mt-4 flex flex-wrap items-start gap-3 rounded-2xl border border-ember-200 bg-ember-50/70 px-4 py-3 text-xs text-ember-800">
            <AlertIcon size={15} className="shrink-0 mt-0.5" />
            <span>{t('accountability.lockWarning')}</span>
          </div>
        </section>
      )}

      {isAuditor && (
        <section className="card mt-4 overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-stone-100 px-5 py-4">
            <div>
              <h2 className="font-display text-lg font-semibold text-forest-950">{t('accountability.reviewQueue')}</h2>
              <p className="mt-0.5 text-[0.68rem] text-forest-800/45">{t('accountability.reviewQueueHint')}</p>
            </div>
            <Badge tone={pendingCorrections.length === 0 ? 'forest' : 'gold'}>{t('accountability.reviewQueueCount', { count: pendingCorrections.length })}</Badge>
          </div>

          {pendingCorrections.length === 0 ? (
            <EmptyState icon={<ShieldIcon size={22} />} title={t('accountability.reviewQueueEmpty')} />
          ) : (
            <ul className="divide-y divide-stone-100">
              {pendingCorrections.map((report) => (
                <li key={report.id} className="px-5 py-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-forest-950">{formatDate(report.businessDate, i18n.language)}</span>
                      <StatusBadge status={report.status} />
                      <span className="text-[0.65rem] text-forest-800/45">{t('accountability.revision', { revision: report.revision })}</span>
                    </div>
                    <span className={`text-sm font-semibold tabular-nums ${report.totalDifferenceMinor === 0 ? 'text-forest-700' : 'text-ember'}`}>
                      {signedMoney(report.totalDifferenceMinor, i18n.language)}
                    </span>
                  </div>
                  <p className="mt-1 text-[0.65rem] text-forest-800/45">
                    {t('accountability.countedVsBook', {
                      counted: formatMoney(report.totalCountedMinor, i18n.language),
                      book: formatMoney(report.totalBookMinor, i18n.language),
                    })}
                    {report.reportedByName && ` · ${t('accountability.submittedBy', { name: report.reportedByName })}`}
                    {report.notes && ` · ${report.notes}`}
                  </p>
                  <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-center">
                    <input
                      className="input flex-1 text-xs"
                      value={reviewNotes[report.id] ?? ''}
                      maxLength={500}
                      placeholder={t('accountability.reviewNotePlaceholder')}
                      onChange={(event) =>
                        setReviewNotes((current) => ({ ...current, [report.id]: event.target.value }))
                      }
                    />
                    <div className="flex gap-2">
                      <button type="button" className="btn-primary" disabled={reviewing === report.id} onClick={() => void handleReview(report, 'APPROVED')}>
                        <CheckIcon size={15} />
                        {t('accountability.approve')}
                      </button>
                      <button type="button" className="btn-secondary" disabled={reviewing === report.id} onClick={() => void handleReview(report, 'REJECTED')}>
                        <AlertIcon size={15} />
                        {t('accountability.reject')}
                      </button>
                    </div>
                  </div>
                  {report.evidence.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {report.evidence.map((evidence) => (
                        <button
                          key={evidence.id}
                          type="button"
                          className="inline-flex items-center gap-1 rounded-full bg-parchment px-2.5 py-1 text-[0.62rem] text-forest-700 hover:bg-forest-50"
                          onClick={() => void openEvidence(report.id, evidence)}
                        >
                          <PaperclipIcon size={11} />
                          {evidence.fileName}
                        </button>
                      ))}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {isLeader && (
        <section className="card mt-4 p-5 sm:p-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="font-display text-xl font-semibold text-forest-950">{t('accountability.formTitle')}</h2>
              <p className="mt-1 max-w-2xl text-xs leading-relaxed text-forest-800/55">{t('accountability.formBody')}</p>
            </div>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-parchment px-3 py-1 text-[0.65rem] font-semibold text-forest-700">
              <LockIcon size={12} /> {t('audit.immutable')}
            </span>
          </div>

          <form onSubmit={handleSubmit} className="mt-5 space-y-4" noValidate>
            <div className="grid gap-3 sm:grid-cols-2">
              {BUCKETS.map((bucket) => {
                const Icon = bucket.icon;
                const counted = parsed[bucket.key];
                const ledgerMinor = book[bucket.key];
                const difference = counted === null ? null : counted - ledgerMinor;
                return (
                  <div key={bucket.key} className="rounded-2xl border border-stone-200 bg-white/70 p-3.5">
                    <div className="flex items-center justify-between gap-2">
                      <label htmlFor={`count-${bucket.key}`} className="text-[0.68rem] font-bold uppercase tracking-[0.1em] text-forest-800/55">
                        {t(bucket.label)}
                      </label>
                      <span className="grid h-7 w-7 place-items-center rounded-lg bg-forest-50 text-forest-600">
                        <Icon size={14} />
                      </span>
                    </div>
                    <input
                      id={`count-${bucket.key}`}
                      className="input mt-2 font-semibold tabular-nums"
                      inputMode="decimal"
                      value={form[bucket.key]}
                      onChange={(event) => setForm((current) => ({ ...current, [bucket.key]: event.target.value }))}
                      placeholder="0"
                      autoComplete="off"
                    />
                    <div className="mt-2 flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-[0.65rem]">
                      <span className="text-forest-800/45">{t('accountability.book')} {formatMoney(ledgerMinor, i18n.language)}</span>
                      <span className={difference === null ? 'text-ember' : difference === 0 ? 'font-semibold text-forest-600' : 'font-semibold text-ember'}>
                        {t('accountability.difference')} {difference === null ? '—' : signedMoney(difference, i18n.language)}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-parchment/60 px-4 py-3 text-xs">
              <div>
                <div className="text-[0.62rem] uppercase tracking-wider text-forest-800/40">{t('accountability.bookTotal')}</div>
                <div className="mt-0.5 font-semibold tabular-nums text-forest-900">{formatMoney(bookTotal, i18n.language)}</div>
              </div>
              <div>
                <div className="text-[0.62rem] uppercase tracking-wider text-forest-800/40">{t('accountability.countedTotal')}</div>
                <div className="mt-0.5 font-semibold tabular-nums text-forest-900">{formTotal === null ? '—' : formatMoney(formTotal, i18n.language)}</div>
              </div>
              <div>
                <div className="text-[0.62rem] uppercase tracking-wider text-forest-800/40">{t('accountability.differenceTotal')}</div>
                <div className={`mt-0.5 font-semibold tabular-nums ${formTotal === null || formTotal === bookTotal ? 'text-forest-900' : 'text-ember'}`}>
                  {formTotal === null ? '—' : signedMoney(formTotal - bookTotal, i18n.language)}
                </div>
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-[0.7fr_1.3fr]">
              <div className="field">
                <label className="label" htmlFor="reconciliation-date">{t('accountability.businessDate')}</label>
                <input
                  id="reconciliation-date"
                  type="date"
                  className="input"
                  value={form.businessDate}
                  min={data.earliestBusinessDate}
                  max={data.today}
                  onChange={(event) => setForm((current) => ({ ...current, businessDate: event.target.value }))}
                />
                <p className="mt-1 text-[0.62rem] text-forest-800/40">{t('accountability.lockHint', { hour: data.lockHour })}</p>
                {data.beforeLock !== false && data.lockHour != null && (
                  <p className="mt-1 text-[0.62rem] text-ember-800/40">{t('accountability.lockCounts')}</p>
                )}
              </div>
              <div className="field">
                <label className="label" htmlFor="reconciliation-notes">{t('accountability.notes')}</label>
                <input
                  id="reconciliation-notes"
                  className="input"
                  value={form.notes}
                  maxLength={500}
                  placeholder={t('accountability.notesPlaceholder')}
                  onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))}
                />
              </div>
            </div>

            <div className="rounded-2xl border border-dashed border-stone-300 bg-white/50 p-3.5">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="text-[0.68rem] font-bold uppercase tracking-[0.1em] text-forest-800/55">{t('accountability.evidenceTitle')}</div>
                  <p className="mt-1 text-[0.65rem] text-forest-800/45">{t('accountability.evidenceHint')}</p>
                </div>
                <label className="btn-secondary cursor-pointer text-xs">
                  <UploadIcon size={15} />
                  {t('accountability.addEvidence')}
                  <input
                    ref={fileInput}
                    type="file"
                    className="sr-only"
                    accept="image/jpeg,image/png,image/webp,application/pdf"
                    multiple
                    onChange={(event) => addFiles(event.target.files)}
                    disabled={evidenceFiles.length >= MAX_EVIDENCE}
                  />
                </label>
              </div>
              {evidenceFiles.length > 0 && (
                <ul className="mt-3 flex flex-wrap gap-2">
                  {evidenceFiles.map((file, index) => (
                    <li key={`${file.name}-${index}`} className="inline-flex items-center gap-1.5 rounded-full bg-forest-50 px-2.5 py-1 text-[0.65rem] text-forest-800">
                      <PaperclipIcon size={12} />
                      <span className="max-w-[10rem] truncate">{file.name}</span>
                      <span className="text-forest-800/40">{formatBytes(file.size)}</span>
                      <button type="button" aria-label={t('common.remove')} className="ml-0.5 text-forest-800/40 hover:text-ember" onClick={() => removeFile(index)}>
                        ×
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {formError && (
              <div className="flex items-start gap-2 rounded-2xl bg-red-50 px-3.5 py-2.5 text-xs text-red-700">
                <AlertIcon size={15} className="mt-0.5 shrink-0" />
                {formError}
              </div>
            )}

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-stone-100 pt-3">
              <span className="text-[0.65rem] text-forest-800/40">{t('accountability.formNote')}</span>
              <button type="submit" className="btn-primary" disabled={submitting}>
                <ShieldIcon size={16} />
                {submitting ? t('accountability.submitting') : t('accountability.submit')}
              </button>
            </div>
          </form>
        </section>
      )}

      {!isLeader && (
        <div className="mt-4 flex items-start gap-3 rounded-3xl border border-forest-100 bg-forest-50/70 px-4 py-3.5 text-xs text-forest-800/70">
          <ShieldIcon size={18} className="mt-0.5 shrink-0 text-forest-600" />
          <p className="leading-relaxed">{t('accountability.memberView')}</p>
        </div>
      )}

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-stone-100 px-5 py-4">
            <div>
              <h2 className="font-display text-lg font-semibold text-forest-950">{t('accountability.history')}</h2>
              <p className="mt-0.5 text-[0.68rem] text-forest-800/45">{t('accountability.historyHint')}</p>
            </div>
            <RefreshIcon size={16} className="text-forest-800/30" />
          </div>
          {data.recentReconciliations.length === 0 ? (
            <EmptyState icon={<ShieldIcon size={22} />} title={t('accountability.emptyHistory')} />
          ) : (
            <ul className="divide-y divide-stone-100">
              {data.recentReconciliations.map((report) => (
                <li key={report.id} className="px-5 py-3.5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-forest-950">{formatDate(report.businessDate, i18n.language)}</span>
                      <StatusBadge status={report.status} />
                      {report.revision > 1 && (
                        <span className="text-[0.62rem] text-forest-800/40">{t('accountability.revision', { revision: report.revision })}</span>
                      )}
                      {(report.revision > 1 || report.reviewStatus !== 'APPROVED') && <ReviewBadge status={report.reviewStatus} />}
                    </div>
                    <span className={`text-sm font-semibold tabular-nums ${report.totalDifferenceMinor === 0 ? 'text-forest-700' : 'text-ember'}`}>
                      {signedMoney(report.totalDifferenceMinor, i18n.language)}
                    </span>
                  </div>
                  <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[0.65rem] text-forest-800/40">
                    <span>
                      {t('accountability.countedVsBook', {
                        counted: formatMoney(report.totalCountedMinor, i18n.language),
                        book: formatMoney(report.totalBookMinor, i18n.language),
                      })}
                    </span>
                    {report.reportedByName && <span>· {t('accountability.submittedBy', { name: report.reportedByName })}</span>}
                  </div>
                  {report.reviewNote && (
                    <p className="mt-1 text-[0.65rem] text-forest-800/50">
                      {t('accountability.reviewNote', {
                        name: report.reviewedByName ?? t('accountability.auditorFallback'),
                        note: report.reviewNote,
                      })}
                    </p>
                  )}
                  {report.evidence.length > 0 && (
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                      {report.evidence.map((evidence) => (
                        <button
                          key={evidence.id}
                          type="button"
                          className="inline-flex items-center gap-1 rounded-full bg-parchment px-2.5 py-1 text-[0.62rem] text-forest-700 hover:bg-forest-50"
                          onClick={() => void openEvidence(report.id, evidence)}
                        >
                          <PaperclipIcon size={11} />
                          {evidence.fileName}
                        </button>
                      ))}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card overflow-hidden">
          <div className="border-b border-stone-100 px-5 py-4">
            <h2 className="font-display text-lg font-semibold text-forest-950">{t('accountability.recentActivity')}</h2>
            <p className="mt-0.5 text-[0.68rem] text-forest-800/45">{t('accountability.activityHint')}</p>
          </div>
          {data.recentEvents.length === 0 ? (
            <EmptyState icon={<LockIcon size={22} />} title={t('accountability.emptyActivity')} />
          ) : (
            <ul className="divide-y divide-stone-100">
              {data.recentEvents.map((event) => {
                const key = `audit.actions.${event.action}`;
                return (
                  <li key={event.id} className="flex items-start gap-3 px-5 py-3">
                    <span className="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-parchment text-forest-600">
                      <LockIcon size={14} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-baseline gap-x-2">
                        <span className="text-xs font-medium text-forest-950">
                          {i18n.exists(key) ? t(key) : event.action.replace(/_/g, ' ').toLowerCase()}
                        </span>
                        {event.actorName && <span className="text-[0.65rem] text-forest-800/45">· {event.actorName}</span>}
                      </div>
                      <div className="mt-0.5 text-[0.62rem] text-forest-800/35">{formatDateTime(event.createdAt, i18n.language)}</div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </div>

      <p className="mt-4 flex items-start gap-2 text-[0.68rem] leading-relaxed text-forest-800/40">
        <LockIcon size={13} className="mt-0.5 shrink-0" />
        {t('accountability.privacyNote')}
      </p>
    </div>
  );
}

function Metric({ label, value, hint, tone }: { label: string; value: string; hint?: string; tone?: ReconciliationStatus }) {
  return (
    <div className="card p-4">
      <div className="flex items-center justify-between">
        <span className="text-[0.68rem] font-semibold uppercase tracking-[0.1em] text-forest-800/45">{label}</span>
        <span className={`grid h-8 w-8 place-items-center rounded-xl ${tone === 'DISCREPANCY' ? 'bg-red-50 text-red-600' : 'bg-forest-50 text-forest-600'}`}>
          {tone === 'DISCREPANCY' ? <AlertIcon size={16} /> : <CheckIcon size={16} />}
        </span>
      </div>
      <div className="mt-2.5 font-display text-xl font-semibold tracking-tight text-forest-950">{value}</div>
      {hint && <div className="mt-1 text-[0.68rem] text-forest-800/40">{hint}</div>}
    </div>
  );
}
