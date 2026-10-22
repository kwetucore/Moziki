interface BrandProps {
  compact?: boolean;
  inverted?: boolean;
}

export function Brand({ compact = false, inverted = false }: BrandProps) {
  return (
    <div className="flex items-center gap-3">
      <span
        className={`relative grid h-10 w-10 shrink-0 place-items-center rounded-2xl ${
          inverted ? 'bg-forest-50/10 ring-1 ring-white/15' : 'bg-forest-900'
        }`}
        aria-hidden="true"
      >
        <svg width="22" height="22" viewBox="0 0 32 32" fill="none">
          <path
            d="M6 24V10l5 7 5-7v14"
            stroke={inverted ? '#f7f5ef' : '#f7f5ef'}
            strokeWidth="2.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M19 24V10l3.5 4.8L26 10v14"
            stroke="#d9a441"
            strokeWidth="2.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <circle cx="16" cy="5.5" r="1.6" fill="#d9a441" />
        </svg>
      </span>
      {!compact && (
        <div className="leading-tight">
          <div
            className={`font-display text-[1.35rem] font-semibold tracking-tight ${
              inverted ? 'text-cream' : 'text-forest-900'
            }`}
          >
            Moziki
          </div>
          <div
            className={`text-[0.6rem] font-semibold uppercase tracking-[0.22em] ${
              inverted ? 'text-forest-200/80' : 'text-forest-600/70'
            }`}
          >
            Coopérative finance
          </div>
        </div>
      )}
    </div>
  );
}
