<template>
  <RequestError :message="error" retry @retry="reload" />
  <p v-if="loading" class="container page">Article is downloading...</p>
  <template v-if="article">
  <div class="banner" data-testid="article-banner">
    <div class="container">
      <h1>{{ article.title }}</h1>

      <ArticleDetailMeta
        v-if="article"
        :article="article"
        @update="updateArticle"
      />
    </div>
  </div>

  <div class="container page">
    <div class="row article-content">
      <!-- eslint-disable vue/no-v-html -->
      <div
        id="article-content"
        class="col-md-12"
        data-testid="article-body"
        v-html="articleHandledBody"
      />
      <!-- eslint-enable vue/no-v-html -->

      <!-- TODO: abstract tag list component -->
      <ul class="tag-list">
        <li
          v-for="tag in article.tagList"
          :key="tag"
          class="tag-default tag-pill tag-outline"
          data-testid="article-tag"
        >
          {{ tag }}
        </li>
      </ul>
    </div>

    <hr>

    <div class="article-actions" data-testid="article-actions">
      <ArticleDetailMeta
        v-if="article"
        :article="article"
        @update="updateArticle"
      />
    </div>
  </div>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import marked from 'src/plugins/marked'
import { api } from 'src/services'
import type { Article } from 'src/services/api'
import ArticleDetailMeta from './ArticleDetailMeta.vue'
import RequestError from './RequestError.vue'
import { useResource } from 'src/composable/use-resource'
import { useUserStore } from 'src/store/user'

const route = useRoute()
const user = useUserStore()
const { data: article, loading, error, reload } = useResource<Article | null>(
  () => [route.params.slug, user.user?.token],
  async () => route.params.slug
    ? (await api.articles.getArticle(String(route.params.slug))).data.article : null,
  null,
)

const articleHandledBody = computed(() => marked(article.value?.body))

function updateArticle(newArticle: Article) {
  article.value = newArticle
}
</script>
