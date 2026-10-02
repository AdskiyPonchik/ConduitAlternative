<template>
  <RouterLink
    :aria-label="props.name"
    :to="{ name: props.name, params: props.params, query: props.query }"
  >
    <slot />
  </RouterLink>
</template>

<script setup lang="ts">
import type { RouteParams } from 'vue-router'
import type { AppRouteNames } from 'src/router'

export interface AppLinkProps {
  name: AppRouteNames
  params?: Partial<RouteParams>
  query?: Record<string, string>
}

const props = withDefaults(defineProps<AppLinkProps>(), {
  params: () => ({}),
  query: () => ({}),
})
</script>
