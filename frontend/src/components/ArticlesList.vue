<template>
  <ArticlesListNavigation
    v-bind="$attrs"
    :tag="tag"
    :username="username"
  />

  <RequestError :message="error" retry @retry="fetchArticles" />
  <div
    v-if="articlesDownloading"
    class="article-preview"
  >
    Articles are downloading...
  </div>

  <div
    v-else-if="!error && articles.length === 0"
    class="article-preview"
  >
    No articles are here... yet.
  </div>

  <template v-else>
    <ArticlesListArticlePreview
      v-for="(article, index) in articles"
      :key="article.slug"
      :article="article"
      @update="newArticle => updateArticle(index, newArticle)"
    />

    <AppPagination
      :count="articlesCount"
      :page="page"
      @page-change="changePage"
    />
  </template>
</template>

<script setup lang="ts">
import { useArticles } from 'src/composable/use-articles'
import AppPagination from './AppPagination.vue'
import ArticlesListArticlePreview from './ArticlesListArticlePreview.vue'
import ArticlesListNavigation from './ArticlesListNavigation.vue'
import RequestError from './RequestError.vue'

const {
  fetchArticles,
  error,
  articlesDownloading,
  articlesCount,
  articles,
  updateArticle,
  page,
  changePage,
  tag,
  username,
} = useArticles()

</script>
