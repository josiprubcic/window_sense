import { defineConfig } from 'vite';

const backend = {
  target: 'http://localhost:3000',
  changeOrigin: false,
  xfwd: true
};

export default defineConfig({
  server: {
    proxy: {
      '/api': backend,
      '/login': backend,
      '/oauth2': backend,
      '/logout': backend
    }
  },
  build: {
    outDir: 'dist'
  }
});
