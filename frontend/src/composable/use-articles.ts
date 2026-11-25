import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { AppRouteNames } from 'src/router'
import { api, pageToOffset } from 'src/services'
import type { Article } from 'src/services/api'
import { useUserStore } from 'src/store/user'
import { useResource } from './use-resource'

export type ArticlesType = 'global-feed' | 'my-feed' | 'tag-feed' | 'user-feed' | 'user-favorites-feed'
export const articlesTypes: ArticlesType[] = ['global-feed', 'my-feed', 'tag-feed', 'user-feed', 'user-favorites-feed']
export const isArticlesType = (type: unknown): type is ArticlesType => articlesTypes.includes(type as ArticlesType)
const types: Partial<Record<AppRouteNames, ArticlesType>> = {
  'global-feed': 'global-feed', 'my-feed': 'my-feed', 'tag': 'tag-feed',
  'profile': 'user-feed', 'profile-favorites': 'user-favorites-feed',
}

export function useArticles() {
  const route = useRoute()
  const user = useUserStore()
  const tag = computed(() => String(route.params.tag ?? ''))
  const username = computed(() => String(route.params.username ?? ''))
  const page = ref(1)
  const meta = computed(() => `${route.name as string}:${tag.value}:${username.value}`)
  watch(meta, () => { page.value = 1 }, { flush: 'sync' })
  const resource = useResource(
    () => [meta.value, page.value, user.user?.token],
    async () => {
      const pagination = pageToOffset(page.value)
      const type = types[route.name as AppRouteNames]
      const response = type === 'my-feed'
        ? await api.articles.getArticlesFeed(pagination)
        : await api.articles.getArticles({ ...pagination,
          ...(type === 'tag-feed' ? { tag: tag.value } : {}),
          ...(type === 'user-feed' ? { author: username.value } : {}),
          ...(type === 'user-favorites-feed' ? { favorited: username.value } : {}),
        })
      return response.data
    },
    { articles: [] as Article[], articlesCount: 0 },
  )
  return {
    fetchArticles: resource.reload, articlesDownloading: resource.loading, error: resource.error,
    articles: computed(() => resource.data.value.articles),
    articlesCount: computed(() => resource.data.value.articlesCount), page, tag, username,
    changePage: (value: number) => { page.value = value },
    updateArticle: (index: number, article: Article) => {
      resource.data.value = { ...resource.data.value,
        articles: resource.data.value.articles.map((item, i) => i === index ? article : item) }
    },
  }
}
