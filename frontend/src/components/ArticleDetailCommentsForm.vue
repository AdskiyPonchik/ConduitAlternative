<template>
  <div class="comment-editor">
  <RequestError :message="error" />
  <p v-if="!profile">
    <AppLink name="login">
      Sign in
    </AppLink> or <AppLink name="register">
      sign up
    </AppLink> to add comments on this article.
  </p>
  <form
    v-else
    class="card comment-form"
    @submit.prevent="submitComment"
  >
    <div class="card-block">
      <textarea
        class="form-control"
        aria-label="Write comment"
        v-model="comment"
        placeholder="Write a comment..."
        :rows="3"
      />
    </div>
    <div class="card-footer">
      <img
        class="comment-author-img"
        :alt="profile.username"
        :src="profile.image"
      >
      <button
        type="submit"
        class="btn btn-sm btn-primary"
        aria-label="Submit"
        :disabled="submitting || !comment.trim()"
      >
        Post Comment
      </button>
    </div>
  </form>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { api } from 'src/services'
import type { Comment } from 'src/services/api'
import { useUserStore } from 'src/store/user'
import RequestError from './RequestError.vue'
import useAsync from 'src/utils/use-async'
import { readDraft, saveDraft, removeDraft } from 'src/utils/drafts'

interface Props {
  articleSlug: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'add-comment', comment: Comment): void
}>()

const { user } = storeToRefs(useUserStore())

const profile = computed(() => user.value)
const owner = ref(user.value?.username ?? '')
watch(() => user.value?.username, value => { if (value) owner.value = value })
const draftKey = computed(() => `comment:${owner.value}:${props.articleSlug}`)
function savedComment(key: string) {
  const saved = readDraft<unknown>(key)
  return typeof saved === 'string' ? saved : ''
}
const comment = ref(savedComment(draftKey.value))
watch(draftKey, key => { comment.value = savedComment(key) })
watch(comment, value => {
  if (value) saveDraft(draftKey.value, value)
  else removeDraft(draftKey.value)
}, { flush: 'sync' })

const { active: submitting, error, run: submitComment } = useAsync(async () => {
  if (!comment.value.trim()) return
  const newComment = await api.articles
    .createArticleComment(props.articleSlug, { comment: { body: comment.value } })
    .then(res => res.data.comment)
  emit('add-comment', newComment)
  comment.value = ''
})
</script>
