import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const mock = (f) => fileURLToPath(new URL(`./shots/${f}`, import.meta.url));

// `vite build --mode shots` swaps Firebase for sample data so the real screens can be photographed. Normal builds are untouched.
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  resolve: mode === 'shots' ? {
    alias: [
      { find: 'firebase/auth', replacement: mock('mock-auth.js') },
      { find: 'firebase/firestore', replacement: mock('mock-firestore.js') },
      { find: /^\.\/firebase\.js$/, replacement: mock('mock-firebase.js') },
    ],
  } : {},
  build: mode === 'shots' ? { outDir: 'dist-shots' } : undefined,
}));
