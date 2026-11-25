<template>
  <RequestError :message="error" retry @retry="reload" />
  <RequestError :message="deleteError" />
  <p v-if="loading">Comments are downloading...</p>
  <ArticleDetailCommentsForm
    :article-slug="slug"
    :key="slug"
    @add-comment="addComment"
  />

  <ArticleDetailComment
    v-for="comment in comments"
    :key="comment.id"
    :comment="comment"
    :username="username"
    :deleting="deleting"
    @remove-comment="() => removeComment(comment.id)"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { api } from 'src/services'
import type { Comment } from 'src/services/api'
import { useUserStore } from 'src/store/user'
import ArticleDetailComment from './ArticleDetailComment.vue'
import ArticleDetailCommentsForm from './ArticleDetailCommentsForm.vue'
import RequestError from './RequestError.vue'
import { useResource } from 'src/composable/use-resource'
import useAsync from 'src/utils/use-async'

const route = useRoute()
const slug = computed(() => String(route.params.slug ?? ''))

const { user } = storeToRefs(useUserStore())

const username = computed(() => user.value?.username)

const { data: comments, loading, error, reload } = useResource<Comment[]>(
  () => [slug.value, user.value?.token],
  async () => slug.value ? (await api.articles.getArticleComments(slug.value)).data.comments : [],
  [],
)

async function addComment(comment: Comment) {
  comments.value = [comment, ...comments.value]
}

const { active: deleting, error: deleteError, run: removeComment } = useAsync(async (commentId: number) => {
  await api.articles.deleteArticleComment(slug.value, commentId)
  comments.value = comments.value.filter(c => c.id !== commentId)
})
</script>
