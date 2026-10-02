<template>
  <div class="article-preview" data-testid="article-preview">
    <div class="article-meta">
      <AppLink
        name="profile"
        :params="{ username: props.article.author.username }"
      >
        <img :alt="props.article.author.username" :src="article.author.image">
      </AppLink>
      <div class="info">
        <AppLink
          class="author"
          name="profile"
          :params="{ username: props.article.author.username }"
        >
          {{ article.author.username }}
        </AppLink>
        <span class="date">{{ new Date(article.createdAt).toDateString() }}</span>
      </div>
      <button
        class="btn btn-sm pull-xs-right"
        :class="[article.favorited ? 'btn-primary' : 'btn-outline-primary']"
        :aria-label="article.favorited ? 'Unfavorite article' : 'Favorite article'"
        :disabled="favoriteProcessGoing"
        @click="() => favoriteArticle()"
      >
        <i class="ion-heart" /> {{ article.favoritesCount }}
      </button>
    </div>

    <AppLink
      class="preview-link"
      name="article"
      :params="{ slug: props.article.slug }"
    >
      <h1>{{ article.title }}</h1>
      <p data-testid="article-description">{{ article.description }}</p>
      <span>Read more...</span>
      <ul class="tag-list" style="display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 4px;">
        <li
          v-for="tag in article.tagList.slice(0, 5)"
          :key="tag"
          class="tag-default tag-pill tag-outline"
          style="max-width: 120px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin: 0;"
        >
          {{ tag }}
        </li>
        <li
          v-if="article.tagList.length > 5"
          class="tag-default tag-pill tag-outline"
          style="margin: 0;"
        >
          +{{ article.tagList.length - 5 }}
        </li>
      </ul>
    </AppLink>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useFavoriteArticle } from 'src/composable/use-favorite-article'
import type { Article } from 'src/services/api'

interface Props {
  article: Article
}
interface Emits {
  (e: 'update', article: Article): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const {
  favoriteProcessGoing,
  favoriteArticle,
} = useFavoriteArticle({
  isFavorited: computed(() => props.article.favorited),
  articleSlug: computed(() => props.article.slug),
  onUpdate: (newArticle: Article): void => emit('update', newArticle),
})
</script>
