<template>
  <aside v-if="message" class="container demo-notice" aria-label="Demo instructions">
    <strong>Demo</strong> — {{ message }}
    <AppLink name="register">Create an account</AppLink>
  </aside>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { CONFIG } from 'src/config'
const message = ref('')
onMounted(async () => {
  try {
    const response = await fetch(`${CONFIG.API_HOST}/api/demo`)
    if (!response.ok) return
    const demo = await response.json()
    if (demo.enabled === true && typeof demo.message === 'string') message.value = demo.message
  }
  catch { /* Optional metadata must not prevent public pages from opening. */ }
})
</script>

<style scoped>
.demo-notice { padding: 16px; background: #edf3e8; margin-top: 12px; }
</style>
