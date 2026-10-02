import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import registerGlobalComponents from './plugins/global-components'
import { restoreSession } from './services'
import { router } from './router'

async function start() {
  await restoreSession()
  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  registerGlobalComponents(app)
  app.mount('#app')
}

void start()
