<template>
  <AppNavigation />
  <DemoNotice />
  <div v-if="sessionNotice" class="container" role="status">
    <p>{{ sessionNotice }} <AppLink name="login">Sign in</AppLink>
      <button type="button" class="btn btn-sm" @click="sessionNotice = ''" aria-label="Dismiss session notice">Dismiss</button>
    </p>
  </div>
  <RouterView />
  <AppFooter />
</template>

<script setup lang="ts">
import AppFooter from './components/AppFooter.vue'
import AppNavigation from './components/AppNavigation.vue'
import DemoNotice from './components/DemoNotice.vue'
import { sessionNotice } from 'src/services/session'
</script>
