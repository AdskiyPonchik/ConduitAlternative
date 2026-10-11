import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import registerGlobalComponents from './plugins/global-components'
import { restoreSession } from './services'
import { router } from './router'

function start() {
  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  registerGlobalComponents(app)
  app.mount('#app')
  // Public pages must remain readable while the API is unavailable.
  void restoreSession()
}

start()
