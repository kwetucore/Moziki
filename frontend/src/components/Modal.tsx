import { useEffect, useId, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { CloseIcon } from './Icons';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: ReactNode;
  width?: 'md' | 'lg';
}

export function Modal({ open, onClose, title, subtitle, children, width = 'md' }: ModalProps) {
  const { t } = useTranslation();
  const titleId = useId();

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-6">
      <button
        type="button"
        aria-label={t('common.close')}
        onClick={onClose}
        className="absolute inset-0 cursor-default bg-forest-950/55 backdrop-blur-[2px]"
        tabIndex={-1}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={`relative max-h-[92vh] w-full overflow-y-auto rounded-t-[1.75rem] border border-white/60 bg-cream shadow-lift animate-fade-up sm:rounded-3xl ${
          width === 'lg' ? 'sm:max-w-2xl' : 'sm:max-w-lg'
        }`}
      >
        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-stone-200/80 bg-cream/95 px-5 py-4 backdrop-blur sm:px-6">
          <div>
            <h2 id={titleId} className="font-display text-xl font-semibold text-forest-950">
              {title}
            </h2>
            {subtitle && <p className="mt-0.5 text-xs text-forest-800/55">{subtitle}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-2 text-forest-800/50 transition hover:bg-parchment hover:text-forest-900"
            aria-label={t('common.close')}
          >
            <CloseIcon size={18} />
          </button>
        </div>
        <div className="px-5 py-5 sm:px-6">{children}</div>
      </div>
    </div>,
    document.body,
  );
}
