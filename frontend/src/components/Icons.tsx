import type { ReactNode, SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Icon({ size = 20, children, ...rest }: IconProps & { children: ReactNode }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...rest}
    >
      {children}
    </svg>
  );
}

export const HomeIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M3 10.5 12 3l9 7.5" />
    <path d="M5 9.5V21h5v-6h4v6h5V9.5" />
  </Icon>
);

export const LedgerIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M5 4.5A2.5 2.5 0 0 1 7.5 2H19v20H7.5A2.5 2.5 0 0 1 5 19.5z" />
    <path d="M5 17.5h14" />
    <path d="M9 7h6M9 11h6" />
  </Icon>
);

export const UsersIcon = (props: IconProps) => (
  <Icon {...props}>
    <circle cx="9" cy="8" r="3.2" />
    <path d="M2.8 20c.5-3.4 3-5.4 6.2-5.4s5.7 2 6.2 5.4" />
    <path d="M16.5 5.2a3.2 3.2 0 0 1 0 6.2M17.5 14.9c2.2.6 3.4 2.3 3.7 5.1" />
  </Icon>
);

export const WalletIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H19a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5.5A2.5 2.5 0 0 1 3 18.5z" />
    <path d="M3 8h15" />
    <path d="M16 13.5h5v4h-5a2 2 0 0 1 0-4Z" />
  </Icon>
);

export const ShieldIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M12 2.5 20 6v6c0 4.8-3.4 8.4-8 9.5-4.6-1.1-8-4.7-8-9.5V6z" />
    <path d="m8.5 12 2.3 2.3 4.7-5" />
  </Icon>
);

export const PlusIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M12 5v14M5 12h14" />
  </Icon>
);

export const OfflineIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M2 8.5a16 16 0 0 1 4.5-2.4M12 5c3.6 0 7 1.3 10 3.5M5.5 12.5A11 11 0 0 1 9 10.9M12 9.5c2.8 0 5.4 1 7.5 2.7" />
    <path d="M8.5 16a6 6 0 0 1 7 0M12 19.5h.01" />
    <path d="m3 3 18 18" />
  </Icon>
);

export const GlobeIcon = (props: IconProps) => (
  <Icon {...props}>
    <circle cx="12" cy="12" r="9" />
    <path d="M3.5 9h17M3.5 15h17" />
    <path d="M12 3c2.5 2.6 3.8 5.7 3.8 9S14.5 18.4 12 21c-2.5-2.6-3.8-5.7-3.8-9S9.5 5.6 12 3Z" />
  </Icon>
);

export const LogoutIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M9 4H5.5A1.5 1.5 0 0 0 4 5.5v13A1.5 1.5 0 0 0 5.5 20H9" />
    <path d="M15 8l4 4-4 4M19 12H9" />
  </Icon>
);

export const SearchIcon = (props: IconProps) => (
  <Icon {...props}>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </Icon>
);

export const ArrowRightIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M4 12h16M14 6l6 6-6 6" />
  </Icon>
);

export const ChevronLeftIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="m15 5-7 7 7 7" />
  </Icon>
);

export const ChevronRightIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="m9 5 7 7-7 7" />
  </Icon>
);

export const CheckIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="m4 12.5 5 5L20 6.5" />
  </Icon>
);

export const CloseIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M6 6l12 12M18 6 6 18" />
  </Icon>
);

export const AlertIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M12 3 2.8 20h18.4z" />
    <path d="M12 9v5M12 17.5h.01" />
  </Icon>
);

export const DepositIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M12 3v12M7.5 10.5 12 15l4.5-4.5" />
    <path d="M4 17v3h16v-3" />
  </Icon>
);

export const WithdrawIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M12 15V3M7.5 7.5 12 3l4.5 4.5" />
    <path d="M4 17v3h16v-3" />
  </Icon>
);

export const CoinsIcon = (props: IconProps) => (
  <Icon {...props}>
    <ellipse cx="9" cy="6.5" rx="6" ry="3" />
    <path d="M3 6.5v4c0 1.7 2.7 3 6 3s6-1.3 6-3v-4" />
    <path d="M3 10.5v4c0 1.7 2.7 3 6 3 1 0 1.9-.1 2.7-.3" />
    <path d="M21 12.5c0 1.7-2.7 3-6 3s-6-1.3-6-3 2.7-3 6-3 6 1.3 6 3Z" />
    <path d="M9 15.5v3c0 1.7 2.7 3 6 3s6-1.3 6-3v-4" />
  </Icon>
);

export const LockIcon = (props: IconProps) => (
  <Icon {...props}>
    <rect x="4.5" y="10" width="15" height="11" rx="2.5" />
    <path d="M8 10V7a4 4 0 0 1 8 0v3" />
    <path d="M12 14.5v2.5" />
  </Icon>
);

export const RefreshIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M20 11a8 8 0 1 0-2.3 5.7" />
    <path d="M20 4v7h-7" />
  </Icon>
);

export const SparkIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M12 3v4M12 17v4M3 12h4M17 12h4" />
    <path d="m6.5 6.5 2.5 2.5M15 15l2.5 2.5M17.5 6.5 15 9M9 15l-2.5 2.5" />
  </Icon>
);

export const MenuIcon = (props: IconProps) => (
  <Icon {...props}>
    <path d="M4 7h16M4 12h16M4 17h16" />
  </Icon>
);
