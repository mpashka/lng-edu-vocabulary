import { execSync } from 'node:child_process'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Версия сборки уезжает в сообщение об ошибке: без неё непонятно, о какой именно
// оболочке речь. Репозитория может не быть (сборка из архива) — тогда «неизвестно»,
// а не падение сборки.
const revision = () => {
  try {
    return execSync('git rev-parse --short HEAD', { stdio: ['ignore', 'pipe', 'ignore'] })
      .toString().trim()
  } catch {
    return 'неизвестно'
  }
}

// Перенаправление `/api` на бэкенд (Spring Boot) — чтобы в разработке
// страница и данные шли с одного источника и не мешали ограничения браузера.
export default defineConfig({
  plugins: [vue()],
  define: {
    __BUILD_REVISION__: JSON.stringify(revision()),
    __BUILD_TIME__: JSON.stringify(new Date().toISOString().slice(0, 16).replace('T', ' '))
  },
  server: {
    port: 8181,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8180',
        changeOrigin: true
      }
    }
  }
})
