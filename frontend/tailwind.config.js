/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#12211b',
        forest: {
          50: '#eef5f0',
          100: '#d8e8dd',
          200: '#b4d2bf',
          300: '#85b59a',
          400: '#529273',
          500: '#33765a',
          600: '#245e47',
          700: '#1c4a39',
          800: '#163b2e',
          900: '#0d2b22',
          950: '#071a14',
        },
        gold: {
          50: '#fdf8ec',
          100: '#f8edcd',
          200: '#f0d998',
          300: '#e6bf5f',
          400: '#d9a441',
          500: '#c88a2b',
          600: '#a96b22',
          700: '#874e1f',
          800: '#713c20',
          900: '#60321e',
        },
        cream: '#f7f5ef',
        parchment: '#efeade',
        clay: '#b8aa93',
        ember: '#b4452f',
      },
      fontFamily: {
        display: ['Georgia', 'Cambria', 'Times New Roman', 'serif'],
        sans: [
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
      },
      boxShadow: {
        card: '0 1px 2px rgba(18, 33, 27, 0.04), 0 12px 32px -20px rgba(18, 33, 27, 0.25)',
        lift: '0 18px 45px -24px rgba(7, 26, 20, 0.55)',
      },
      borderRadius: {
        '4xl': '2rem',
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-400px 0' },
          '100%': { backgroundPosition: '400px 0' },
        },
      },
      animation: {
        'fade-up': 'fade-up 0.4s ease-out both',
      },
    },
  },
  plugins: [],
};
