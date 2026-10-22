import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { api, isNetworkError } from '@/lib/api';
import { errorMessage } from '@/lib/useAsync';
import { enqueueTransaction } from '@/lib/offlineQueue';
import type {
  Member,
  PaymentChannel,
  PostTransactionPayload,
  TransactionKind,
} from '@/lib/types';
import { Modal } from './Modal';
import { AlertIcon, LockIcon, OfflineIcon } from './Icons';

const KINDS: TransactionKind[] = [
  'DEPOSIT',
  'WITHDRAWAL',
  'SHARE_PURCHASE',
  'LOAN_REPAYMENT',
  'LOAN_DISBURSEMENT',
  'FEE',
];

const CHANNELS: PaymentChannel[] = ['CASH', 'AIRTEL_MONEY', 'ORANGE_MONEY', 'MPESA'];

interface TransactionModalProps {
  open: boolean;
  onClose: () => void;
  onPosted: (message: string, queued?: boolean) => void;
  presetKind?: TransactionKind;
}

export function TransactionModal({ open, onClose, onPosted, presetKind }: TransactionModalProps) {
  const { t } = useTranslation();
  const [kind, setKind] = useState<TransactionKind>(presetKind ?? 'DEPOSIT');
  const [memberId, setMemberId] = useState('');
  const [amount, setAmount] = useState('');
  const [channel, setChannel] = useState<PaymentChannel>('CASH');
  const [description, setDescription] = useState('');
  const [descriptionTouched, setDescriptionTouched] = useState(false);
  const [members, setMembers] = useState<Member[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [membersError, setMembersError] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [online, setOnline] = useState(() => navigator.onLine);

  const needsMember = kind !== 'FEE';
  const selectedMember = useMemo(
    () => members.find((member) => member.id === memberId),
    [memberId, members],
  );

  const defaultDescription = useMemo(() => {
    if (kind === 'FEE') {
      return t('transactions.defaultDescription.FEE');
    }
    return t(`transactions.defaultDescription.${kind}`, {
      name: selectedMember?.fullName ?? '',
    });
  }, [kind, selectedMember, t]);

  useEffect(() => {
    const handleOnline = () => setOnline(true);
    const handleOffline = () => setOnline(false);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  useEffect(() => {
    if (!open) {
      return;
    }
    setKind(presetKind ?? 'DEPOSIT');
    setMemberId('');
    setAmount('');
    setChannel('CASH');
    setDescriptionTouched(false);
    setError(null);
    setSubmitting(false);
    setIdempotencyKey(crypto.randomUUID());

    let cancelled = false;
    setMembersLoading(true);
    setMembersError(null);
    api
      .members()
      .then((data) => {
        if (!cancelled) {
          setMembers(data);
        }
      })
      .catch((loadError: unknown) => {
        if (!cancelled) {
          setMembersError(errorMessage(loadError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setMembersLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [open, presetKind]);

  useEffect(() => {
    if (!descriptionTouched) {
      setDescription(defaultDescription);
    }
  }, [defaultDescription, descriptionTouched]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);

    const normalized = amount.replace(/\s/g, '').replace(',', '.');
    if (!/^\d{1,13}(\.\d{1,2})?$/.test(normalized) || Number(normalized) <= 0) {
      setError(t('common.invalidAmount'));
      return;
    }
    if (needsMember && !memberId) {
      setError(t('transactions.selectMember'));
      return;
    }

    const finalDescription = description.trim() || defaultDescription;
    const payload: PostTransactionPayload = {
      kind,
      memberId: needsMember ? memberId : null,
      amount: normalized,
      channel,
      description: finalDescription,
      idempotencyKey,
    };

    setSubmitting(true);
    try {
      if (!navigator.onLine) {
        await enqueueTransaction({
          id: crypto.randomUUID(),
          payload,
          createdAt: new Date().toISOString(),
          summary: finalDescription,
          kindLabel: t(`kinds.${kind}`),
          amountMinor: Math.round(Number(normalized) * 100),
        });
        onPosted(t('transactions.offlineQueued'), true);
        onClose();
        return;
      }

      await api.createTransaction(payload);
      onPosted(t('transactions.success'));
      onClose();
    } catch (submitError) {
      if (isNetworkError(submitError)) {
        await enqueueTransaction({
          id: crypto.randomUUID(),
          payload,
          createdAt: new Date().toISOString(),
          summary: finalDescription,
          kindLabel: t(`kinds.${kind}`),
          amountMinor: Math.round(Number(normalized) * 100),
        });
        onPosted(t('transactions.offlineQueued'), true);
        onClose();
        return;
      }
      setError(errorMessage(submitError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('transactions.newTitle')}
      subtitle={`${t('transactions.kindHint')} · CDF`}
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {!online && (
          <div className="flex items-start gap-2 rounded-2xl border border-gold-200 bg-gold-50 px-3 py-2.5 text-xs text-gold-800">
            <OfflineIcon size={16} className="mt-0.5 shrink-0" />
            <span>{t('transactions.offlineQueued')}</span>
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="field">
            <label className="label" htmlFor="tx-kind">
              {t('transactions.kind')}
            </label>
            <select
              id="tx-kind"
              className="select"
              value={kind}
              onChange={(event) => {
                setKind(event.target.value as TransactionKind);
                setDescriptionTouched(false);
              }}
            >
              {KINDS.map((option) => (
                <option key={option} value={option}>
                  {t(`kinds.${option}`)}
                </option>
              ))}
            </select>
          </div>

          <div className="field">
            <label className="label" htmlFor="tx-amount">
              {t('transactions.amount')}
            </label>
            <div className="relative">
              <input
                id="tx-amount"
                className="input pr-11 font-semibold tabular-nums"
                inputMode="decimal"
                placeholder="0"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                autoComplete="off"
                autoFocus
              />
              <span className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 text-[0.68rem] font-bold uppercase tracking-wider text-forest-800/40">
                FC
              </span>
            </div>
          </div>
        </div>

        {needsMember && (
          <div className="field">
            <label className="label" htmlFor="tx-member">
              {t('transactions.memberHint')}
            </label>
            <select
              id="tx-member"
              className="select"
              value={memberId}
              onChange={(event) => {
                setMemberId(event.target.value);
                setDescriptionTouched(false);
              }}
              disabled={membersLoading}
            >
              <option value="">{membersLoading ? t('common.loading') : t('transactions.selectMember')}</option>
              {members.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.fullName} · {member.memberNumber}
                </option>
              ))}
            </select>
            {membersError && <p className="text-xs text-ember">{membersError}</p>}
            {!membersLoading && !membersError && members.length === 0 && (
              <p className="text-xs text-gold-700">{t('transactions.noMembers')}</p>
            )}
          </div>
        )}

        <div>
          <span className="label">{t('transactions.channel')}</span>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {CHANNELS.map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setChannel(option)}
                aria-pressed={channel === option}
                className={`rounded-2xl border px-2 py-2.5 text-xs font-semibold transition ${
                  channel === option
                    ? 'border-forest-700 bg-forest-900 text-cream shadow-sm'
                    : 'border-stone-300/80 bg-white text-forest-800/70 hover:border-forest-300'
                }`}
              >
                {t(`channels.${option}`)}
              </button>
            ))}
          </div>
          <p className="help mt-1.5">{t('transactions.channelHint')}</p>
        </div>

        <div className="field">
          <label className="label" htmlFor="tx-description">
            {t('transactions.descriptionLabel')}
          </label>
          <textarea
            id="tx-description"
            className="textarea"
            value={description}
            placeholder={t('transactions.descriptionPlaceholder')}
            onChange={(event) => {
              setDescription(event.target.value);
              setDescriptionTouched(true);
            }}
          />
        </div>

        <div className="flex items-start gap-2 rounded-2xl bg-parchment/70 px-3 py-2.5 text-[0.7rem] leading-relaxed text-forest-800/60">
          <LockIcon size={15} className="mt-0.5 shrink-0 text-forest-600" />
          <span>{t('transactions.doubleEntryNote')}</span>
        </div>

        {error && (
          <div role="alert" className="flex items-start gap-2 rounded-2xl bg-red-50 px-3 py-2.5 text-xs text-red-700">
            <AlertIcon size={16} className="mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="flex items-center justify-end gap-2 border-t border-stone-200/80 pt-4">
          <button type="button" className="btn-secondary" onClick={onClose}>
            {t('common.cancel')}
          </button>
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? t('transactions.submitting') : t('transactions.submit')}
          </button>
        </div>
      </form>
    </Modal>
  );
}
