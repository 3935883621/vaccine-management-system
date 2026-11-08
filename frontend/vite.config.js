import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// 服务器配置统一在根目录 server.config.json 中维护（前端端口 / 后端代理目标）
const serverConfig = JSON.parse(
  readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../server.config.json'), 'utf-8'),
)

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: serverConfig.frontend.port,
    proxy: {
      // 开发环境代理：前端 /api 请求转发到后端（端口取自 server.config.json）
      '/api': {
        target: `http://localhost:${serverConfig.backend.port}`,
        changeOrigin: true,
      },
    },
  },
})
