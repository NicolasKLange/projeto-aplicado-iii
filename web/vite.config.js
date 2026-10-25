import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// A porta 5173 é a única origem liberada no CORS da API (ORIGENS_WEB, infra/deploy.sh).
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
});
