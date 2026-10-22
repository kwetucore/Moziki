import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from '@/locales/en.json';
import fr from '@/locales/fr.json';
import sw from '@/locales/sw.json';

export const LANGUAGES = [
  { code: 'en', label: 'EN', name: 'English' },
  { code: 'fr', label: 'FR', name: 'Français' },
  { code: 'sw', label: 'SW', name: 'Kiswahili' },
] as const;

export type LanguageCode = (typeof LANGUAGES)[number]['code'];

const STORAGE_KEY = 'moziki.lang';

export function isLanguageCode(value: string | null): value is LanguageCode {
  return value === 'en' || value === 'fr' || value === 'sw';
}

const stored = localStorage.getItem(STORAGE_KEY);
const initialLanguage: LanguageCode = isLanguageCode(stored) ? stored : 'fr';

void i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    fr: { translation: fr },
    sw: { translation: sw },
  },
  lng: initialLanguage,
  fallbackLng: 'fr',
  supportedLngs: ['en', 'fr', 'sw'],
  interpolation: { escapeValue: false },
  returnNull: false,
});

document.documentElement.lang = initialLanguage;

export async function changeLanguage(code: LanguageCode): Promise<void> {
  await i18n.changeLanguage(code);
  localStorage.setItem(STORAGE_KEY, code);
  document.documentElement.lang = code;
}

export function currentLanguage(): LanguageCode {
  return isLanguageCode(i18n.language) ? i18n.language : 'fr';
}

export default i18n;
