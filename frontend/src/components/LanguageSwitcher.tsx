import { useTranslation } from 'react-i18next';
import { LANGUAGES, changeLanguage, currentLanguage, type LanguageCode } from '@/i18n';

interface LanguageSwitcherProps {
  inverted?: boolean;
}

export function LanguageSwitcher({ inverted = false }: LanguageSwitcherProps) {
  const { t } = useTranslation();
  const active = currentLanguage();

  const select = (code: LanguageCode) => {
    void changeLanguage(code);
  };

  return (
    <div
      className={`inline-flex items-center rounded-full p-0.5 ${
        inverted ? 'bg-white/10 ring-1 ring-white/15' : 'bg-parchment ring-1 ring-black/5'
      }`}
      role="group"
      aria-label={t('app.language')}
    >
      {LANGUAGES.map((language) => {
        const selected = active === language.code;
        return (
          <button
            key={language.code}
            type="button"
            onClick={() => select(language.code)}
            aria-pressed={selected}
            title={language.name}
            className={`rounded-full px-2.5 py-1 text-[0.68rem] font-semibold uppercase tracking-wider transition ${
              selected
                ? inverted
                  ? 'bg-gold-400 text-forest-950 shadow-sm'
                  : 'bg-white text-forest-900 shadow-sm'
                : inverted
                  ? 'text-forest-100/70 hover:text-cream'
                  : 'text-forest-700/60 hover:text-forest-900'
            }`}
          >
            {language.label}
          </button>
        );
      })}
    </div>
  );
}
