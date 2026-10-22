import type { TFunction } from 'i18next';
import type { AccountCategory } from './types';

/**
 * Chart-of-accounts labels are stored once, in the database, with the name the
 * cooperative uses on paper. The UI translates the standard accounts by code so
 * the ledger reads naturally in EN / FR / SW, while custom accounts keep the
 * name their cooperative gave them.
 */
const LABEL_BY_CODE: Record<string, string> = {
  '1000': 'accounts.names.cash',
  '1010': 'accounts.names.airtel',
  '1020': 'accounts.names.orange',
  '1030': 'accounts.names.mpesa',
  '1100': 'accounts.names.loansReceivable',
  '4000': 'accounts.names.feeIncome',
  '4100': 'accounts.names.interestIncome',
};

const LABEL_BY_CATEGORY: Partial<Record<AccountCategory, string>> = {
  SAVINGS: 'accounts.names.savings',
  SHARES: 'accounts.names.shares',
};

interface AccountLike {
  code: string;
  name: string;
  category?: AccountCategory;
}

export function localizedAccountName(t: TFunction, account: AccountLike): string {
  const byCode = LABEL_BY_CODE[account.code];
  if (byCode) {
    return t(byCode);
  }
  const byCategory = account.category ? LABEL_BY_CATEGORY[account.category] : undefined;
  if (byCategory) {
    return t(byCategory);
  }
  // Member ledgers use 2000-M-xxxxx (savings) and 3000-M-xxxxx (shares).
  if (account.code.startsWith('2000')) {
    return t('accounts.names.savings');
  }
  if (account.code.startsWith('3000')) {
    return t('accounts.names.shares');
  }
  return account.name;
}
