<template>
  <div v-if="message" class="request-error" role="alert">
    <p>{{ message }}</p>
    <button v-if="retry" class="btn btn-sm btn-outline-secondary" type="button" @click="emit('retry')">Try again</button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ message: string, retry?: boolean }>()
const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<style scoped>
.request-error { color: #a32626; padding: 12px 0; }
</style>
