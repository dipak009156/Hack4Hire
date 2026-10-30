/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        cream: "#FBF5EC",
        sand: "#E6D5BF",
        espresso: "#2B1D16",
        brown: "#3B2A20",
        terracotta: "#C8553D",
        amber: "#E9A23B",
        olive: "#6B8F4E",
        brick: "#A63D2F",
      },
      fontFamily: {
        fraunces: ['Fraunces', 'Georgia', 'serif'],
        inter: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
      boxShadow: {
        warm: '0 4px 20px -2px rgba(43, 29, 22, 0.08), 0 2px 6px -1px rgba(43, 29, 22, 0.04)',
        'warm-lg': '0 10px 25px -3px rgba(43, 29, 22, 0.12), 0 4px 10px -2px rgba(43, 29, 22, 0.06)',
      },
      borderRadius: {
        'xl': '12px',
      }
    },
  },
  plugins: [],
}

