const INTL_LOCALES: Record<string, string> = {
  en: 'en-CD',
  fr: 'fr-CD',
  sw: 'sw',
};

function intlLocale(language: string): string {
  return INTL_LOCALES[language] ?? 'fr-CD';
}

/** Money is stored in minor units; CDF has no commonly used subunit, so hide .00. */
export function formatMoney(minor: number, language = 'fr', withCurrency = true): string {
  const formatter = new Intl.NumberFormat(intlLocale(language), {
    style: withCurrency ? 'currency' : 'decimal',
    currency: 'CDF',
    currencyDisplay: 'code',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
  return formatter.format(minor / 100).replace('CDF', 'FC').replace(/\u00a0/g, ' ');
}

export function formatNumber(value: number, language = 'fr'): string {
  return new Intl.NumberFormat(intlLocale(language)).format(value);
}

export function formatDate(iso: string | null | undefined, language = 'fr'): string {
  if (!iso) {
    return '—';
  }
  return new Intl.DateTimeFormat(intlLocale(language), {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(new Date(iso));
}

export function formatDateTime(iso: string | null | undefined, language = 'fr'): string {
  if (!iso) {
    return '—';
  }
  return new Intl.DateTimeFormat(intlLocale(language), {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));
}

export function initials(fullName: string): string {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

export function todayLabel(language = 'fr'): string {
  return new Intl.DateTimeFormat(intlLocale(language), {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date());
}

export function minorToInput(minor: number): string {
  return (minor / 100).toFixed(2).replace(/\.00$/, '');
}
