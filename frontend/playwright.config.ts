import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: 'http://127.0.0.1:14173', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: '../mvnw spring-boot:test-run -Dspring-boot.run.main-class=de.conduit.E2eApplication -Dspring-boot.run.arguments="--spring.config.import= --server.port=18080 --conduit.demo.enabled=true --conduit.demo.database-name=conduit_test_demo --conduit.demo.password=conduit-e2e-password-123 --conduit.demo.limits.enabled=false --conduit.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=" -f ../pom.xml',
      url: 'http://127.0.0.1:18080/api/demo',
      timeout: 180_000,
      stdout: 'pipe',
      reuseExistingServer: false,
    },
    {
      command: 'pnpm dev --host 127.0.0.1 --port 14173 --strictPort',
      env: { VITE_API_PROXY_TARGET: 'http://127.0.0.1:18080', VITE_API_HOST: '' },
      url: 'http://127.0.0.1:14173',
      reuseExistingServer: false,
    },
  ],
})
