/// <reference types="vitest" />
import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import analyzer from 'rollup-plugin-analyzer'
import { defineConfig, loadEnv } from 'vite'
import type { CoverageOptions } from 'vitest/node'
import mcrOptions from './config/mcr.unit.config'

const isTesting = process.env.NODE_ENV === 'testing'
const allowAnalysis = process.env.ANALYZE === 'true'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    server: {
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
    resolve: {
      alias: {
        src: fileURLToPath(new URL('src', import.meta.url)),
      },
    },
    build: {
      sourcemap: isTesting,
    },
    plugins: [
      vue(),
      allowAnalysis && analyzer({ summaryOnly: true }),
    ],
    test: {
      include: [
        'src/**/*.spec.ts',
      ],
      environment: 'happy-dom',
      setupFiles: './src/setup-tests.ts',
      globals: true,
      snapshotFormat: {
        escapeString: false,
      },
      coverage: {
        enabled: true,
        reportsDirectory: './coverage/unit',
        provider: 'custom',
        customProviderModule: 'vitest-monocart-coverage',
        coverageReportOptions: mcrOptions,
        include: [
          'src',
        ],
        exclude: [
          'src/services/api.ts',
          'src/setup-tests.ts',
          'src/utils/test',
          '**/*.d.ts',
        ],
        all: true,
      } as CoverageOptions,
    },
  }
})
