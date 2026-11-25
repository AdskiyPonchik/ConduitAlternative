<template>
  <p>Popular Tags</p>
  <RequestError :message="error" retry @retry="reload" />
  <p v-if="loading">Popular tags are downloading...</p>

  <div class="tag-list">
    <AppLink
      v-for="tag in tags"
      :key="tag"
      class="tag-pill tag-default"
      :aria-label="tag"
      name="tag"
      :params="{ tag }"
    >
      {{ tag }}
    </AppLink>
  </div>
</template>

<script setup lang="ts">
import { api } from 'src/services'
import { useResource } from 'src/composable/use-resource'
import RequestError from './RequestError.vue'
const { data: tags, error, loading, reload } = useResource(
  () => 'tags', async () => (await api.tags.getTags()).data.tags, [] as string[],
)
</script>
