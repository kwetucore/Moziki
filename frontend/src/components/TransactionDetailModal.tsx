import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { errorMessage, useAsync } from '@/lib/useAsync';
import { formatDateTime, formatMoney } from '@/lib/format';
import { localizedAccountName } from '@/lib/accountNames';
import { useAuth } from '@/lib/auth';
import { Modal } from './Modal';
import { CheckIcon, LockIcon } from './Icons';
import { Badge, ChannelLabel, KindBadge, LoadingBlock, ErrorState, StatusBadge } from './ui';

interface TransactionDetailModalProps {
  open: boolean;
  transactionId: string | null;
  onClose: () => void;
  onChanged: (message: string) => void;
}

export function TransactionDetailModal({
  open,
  transactionId,
  onClose,
  onChanged,
}: TransactionDetailModalProps) {
  const { t, i18n } = useTranslation();
  const { hasRole } = useAuth();
  const { data, loading, error, reload } = useAsync(
    async () => (transactionId ? api.transaction(transactionId) : null),
    [transactionId],
  );
  const [showReverse, setShowReverse] = useState(false);
  const [reason, setReason] = useState('');
  const [reversing, setReversing] = useState(false);
  const [reverseError, setReverseError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    setShowReverse(false);
    setReason('');
    setReverseError(null);
    setNotice(null);
  }, [transactionId, open]);

  const handleReverse = async () => {
    if (!data) {
      return;
    }
    if (!reason.trim()) {
      setReverseError(t('common.required'));
      return;
    }
    setReversing(true);
    setReverseError(null);
    try {
      await api.reverseTransaction(data.id, reason.trim());
      setShowReverse(false);
      setReason('');
      setNotice(t('transactions.reversalSuccess'));
      onChanged(t('transactions.reversalSuccess'));
      reload();
    } catch (reverseFailure) {
      setReverseError(errorMessage(reverseFailure));
    } finally {
      setReversing(false);
    }
  };

  const debitTotal =
    data?.entries?.filter((entry) => entry.direction === 'DEBIT').reduce((sum, entry) => sum + entry.amountMinor, 0) ??
    0;
  const creditTotal =
    data?.entries?.filter((entry) => entry.direction === 'CREDIT').reduce((sum, entry) => sum + entry.amountMinor, 0) ??
    0;
  const canReverse = data && data.status === 'POSTED' && data.kind !== 'REVERSAL' && hasRole('LEADER');

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('transactions.details')}
      subtitle={data?.reference}
      width="lg"
    >
      {loading && <LoadingBlock rows={4} />}
      {!loading && error && <ErrorState message={error} onRetry={reload} />}
      {!loading && data && (
        <div className="space-y-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div className="mb-2 flex flex-wrap items-center gap-2">
                <KindBadge kind={data.kind} />
                <StatusBadge status={data.status} />
                {data.source === 'USSD' && <Badge tone="violet">USSD</Badge>}
              </div>
              <div className="font-display text-3xl font-semibold tracking-tight text-forest-950">
                {formatMoney(data.amountMinor, i18n.language)}
              </div>
              <p className="mt-1 max-w-lg text-sm text-forest-800/60">{data.description}</p>
            </div>
            {canReverse && !showReverse && (
              <button type="button" className="btn-secondary" onClick={() => setShowReverse(true)}>
                {t('transactions.reverse')}
              </button>
            )}
          </div>

          {data.status === 'REVERSED' && (
            <div className="rounded-2xl border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700">
              {t('transactions.reversedNotice')}
              {data.reversalReason && <p className="mt-1 font-medium">{data.reversalReason}</p>}
            </div>
          )}

          {notice && (
            <div className="flex items-center gap-2 rounded-2xl border border-forest-200 bg-forest-50 px-3.5 py-3 text-xs font-medium text-forest-800">
              <CheckIcon size={16} className="text-forest-600" />
              {notice}
            </div>
          )}

          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 rounded-2xl bg-white/70 px-4 py-3.5 text-xs sm:grid-cols-3">
            <div>
              <dt className="text-forest-800/45">{t('transactions.date')}</dt>
              <dd className="mt-0.5 font-medium text-forest-900">
                {formatDateTime(data.createdAt, i18n.language)}
              </dd>
            </div>
            <div>
              <dt className="text-forest-800/45">{t('transactions.member')}</dt>
              <dd className="mt-0.5 font-medium text-forest-900">
                {data.memberName ?? t('common.notAvailable')}
              </dd>
            </div>
            <div>
              <dt className="text-forest-800/45">{t('transactions.channel')}</dt>
              <dd className="mt-0.5 font-medium text-forest-900">
                <ChannelLabel channel={data.channel} />
              </dd>
            </div>
            <div>
              <dt className="text-forest-800/45">{t('transactions.postedBy')}</dt>
              <dd className="mt-0.5 font-medium text-forest-900">
                {data.createdByName ?? t('common.notAvailable')}
              </dd>
            </div>
            <div>
              <dt className="text-forest-800/45">{t('transactions.reference')}</dt>
              <dd className="mt-0.5 font-mono text-[0.7rem] font-medium text-forest-900">
                {data.reference}
              </dd>
            </div>
            {data.externalRef && (
              <div>
                <dt className="text-forest-800/45">Mobile money ref.</dt>
                <dd className="mt-0.5 font-medium text-forest-900">{data.externalRef}</dd>
              </div>
            )}
          </dl>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-forest-950">{t('transactions.entries')}</h3>
              <span className="inline-flex items-center gap-1 text-[0.68rem] font-semibold text-forest-600">
                <LockIcon size={13} /> {t('audit.immutable')}
              </span>
            </div>
            <div className="overflow-hidden rounded-2xl border border-stone-200 bg-white/80">
              <table className="w-full text-left text-xs">
                <thead className="bg-parchment/60">
                  <tr>
                    <th className="table-head">{t('transactions.account')}</th>
                    <th className="table-head text-right">{t('transactions.debit')}</th>
                    <th className="table-head text-right">{t('transactions.credit')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100">
                  {(data.entries ?? []).map((entry) => (
                    <tr key={entry.id}>
                      <td className="px-4 py-2.5">
                        <div className="font-medium text-forest-900">
                          {localizedAccountName(t, {
                            code: entry.accountCode ?? '',
                            name: entry.accountName ?? '',
                          })}
                        </div>
                        <div className="font-mono text-[0.62rem] text-forest-800/40">
                          {entry.accountCode}
                        </div>
                      </td>
                      <td className="px-4 py-2.5 text-right font-medium tabular-nums text-forest-900">
                        {entry.direction === 'DEBIT'
                          ? formatMoney(entry.amountMinor, i18n.language, false)
                          : '—'}
                      </td>
                      <td className="px-4 py-2.5 text-right font-medium tabular-nums text-forest-900">
                        {entry.direction === 'CREDIT'
                          ? formatMoney(entry.amountMinor, i18n.language, false)
                          : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot className="border-t border-stone-200 bg-parchment/40">
                  <tr>
                    <td className="px-4 py-2.5 text-[0.68rem] font-semibold uppercase tracking-wider text-forest-800/50">
                      {t('transactions.balanced')}
                    </td>
                    <td className="px-4 py-2.5 text-right font-semibold tabular-nums">
                      {formatMoney(debitTotal, i18n.language, false)}
                    </td>
                    <td className="px-4 py-2.5 text-right font-semibold tabular-nums">
                      {formatMoney(creditTotal, i18n.language, false)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          {showReverse && (
            <div className="rounded-2xl border border-gold-200 bg-gold-50/60 p-4">
              <h3 className="text-sm font-semibold text-forest-950">{t('transactions.reverseTitle')}</h3>
              <p className="mt-1 text-xs text-forest-800/60">{t('transactions.doubleEntryNote')}</p>
              <label className="label mt-3" htmlFor="reverse-reason">
                {t('transactions.reverseReason')}
              </label>
              <textarea
                id="reverse-reason"
                className="textarea bg-white"
                value={reason}
                placeholder={t('transactions.reverseReasonPlaceholder')}
                onChange={(event) => setReason(event.target.value)}
              />
              {reverseError && <p className="mt-2 text-xs text-ember">{reverseError}</p>}
              <div className="mt-3 flex justify-end gap-2">
                <button type="button" className="btn-secondary" onClick={() => setShowReverse(false)}>
                  {t('common.cancel')}
                </button>
                <button
                  type="button"
                  className="btn-danger"
                  disabled={reversing}
                  onClick={() => void handleReverse()}
                >
                  {reversing ? t('transactions.reversing') : t('transactions.reverseConfirm')}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
