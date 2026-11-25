<template>
  <div class="editor-page">
    <div class="container page">
      <div class="row">
        <div class="col-md-10 offset-md-1 col-xs-12">
          <ul v-if="errors.length" class="error-messages">
            <li v-for="error in errors" :key="error">{{ error }}</li>
          </ul>
          <RequestError :message="loadError" retry @retry="loadArticle" />
          <p v-if="loading">Article is downloading...</p>
          <p v-if="draftRestored" role="status">Your draft has been restored.</p>
          <form @submit.prevent="onSubmit">
            <fieldset class="form-group">
              <input
                type="text"
                class="form-control form-control-lg"
                aria-label="Title"
                v-model="form.title"
                placeholder="Article Title"
              >
            </fieldset>
            <fieldset class="form-group">
              <input
                type="text"
                class="form-control form-control-lg"
                aria-label="Description"
                v-model="form.description"
                placeholder="What's this article about?"
              >
            </fieldset>

            <div class="row">
              <div class="col-md-6">
                <fieldset class="form-group">
                  <label><strong>Article content (Markdown)</strong></label>
                  <textarea
                    class="form-control"
                    aria-label="Body"
                    v-model="form.body"
                    placeholder="Write your article (in markdown)"
                    :rows="12"
                  />
                </fieldset>
              </div>
              <div class="col-md-6">
                <fieldset class="form-group">
                  <label><strong>Live preview</strong></label>
                  <div
                    id="article-content"
                    class="form-control article-content"
                    v-html="renderedBody"
                    style="min-height: 290px; height: auto; overflow-y: auto; background-color: #fafafa; border: 1px solid #ccc; padding: 10px; border-radius: 4px;"
                  />
                </fieldset>
              </div>
            </div>

            <fieldset class="form-group">
              <input
                v-if="!slug"
                type="text"
                class="form-control"
                aria-label="Tags"
                v-model="newTag"
                placeholder="Enter tags"
                @change="addTag"
                @keypress.enter.prevent="addTag"
              >
              <div class="tag-list">
                <span
                  v-for="tag in form.tagList"
                  :key="tag"
                  class="tag-default tag-pill"
                >
                  <i
                    v-if="!slug"
                    class="ion-close-round"
                    role="button"
                    :aria-label="`Delete tag: ${tag}`"
                    tabindex="0"
                    @click="removeTag(tag)"
                    @keypress.enter="removeTag(tag)"
                  />
                  {{ tag }}
                </span>
              </div>
            </fieldset>
            <button
              type="submit"
              class="btn btn-lg pull-xs-right btn-primary"
              :disabled="loading || !!loadError || saving || !(form.title && form.description && form.body)"
            >
              Publish Article
            </button>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, isFetchError } from 'src/services'
import type { Article } from 'src/services/api'
import renderMarkdown from 'src/plugins/marked'
import { useUserStore } from 'src/store/user'
import RequestError from 'src/components/RequestError.vue'
import { readDraft, saveDraft, removeDraft } from 'src/utils/drafts'
import { errorMessages } from 'src/utils/errors'

interface FormState {
  title: string
  description: string
  body: string
  tagList: string[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const slug = computed<string>(() => route.params.slug as string)

const form: FormState = reactive({
  title: '',
  description: '',
  body: '',
  tagList: [],
})

const newTag = ref<string>('')
const errors = ref<string[]>([])
const saving = ref(false)
function addTag() {
  const tag = newTag.value.trim()
  if (tag && !form.tagList.includes(tag)) form.tagList.push(tag)
  newTag.value = ''
}
function removeTag(tag: string) {
  form.tagList = form.tagList.filter(t => t !== tag)
}

const renderedBody = computed(() => renderMarkdown(form.body))

const loading = ref(false)
const loadError = ref('')
const draftRestored = ref(false)
const owner = userStore.user?.username ?? ''
const draftKey = computed(() => `article:${owner}:${slug.value || 'new'}`)
let generation = 0
let draftReady = false

async function loadArticle() {
  const current = ++generation
  draftReady = false
  loadError.value = ''
  loading.value = true
  if (!userStore.isAuthorized) {
    loading.value = false
    await router.replace({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const article = slug.value ? (await api.articles.getArticle(slug.value)).data.article : null
    if (current !== generation) return
    Object.assign(form, article ? {
      title: article.title, description: article.description, body: article.body, tagList: article.tagList,
    } : { title: '', description: '', body: '', tagList: [] })
    const draft = readDraft<FormState>(draftKey.value)
    draftRestored.value = !!draft && typeof draft.title === 'string' && typeof draft.description === 'string'
      && typeof draft.body === 'string' && Array.isArray(draft.tagList) && draft.tagList.every(tag => typeof tag === 'string')
    if (draftRestored.value) Object.assign(form, draft)
    draftReady = true
  }
  catch (error) { if (current === generation) loadError.value = errorMessages(error).join(' ') }
  finally { if (current === generation) loading.value = false }
}

watch(slug, () => {
  if (route.name === 'create-article' || route.name === 'edit-article') void loadArticle()
}, { immediate: true })
onScopeDispose(() => { generation++ })
watch(form, () => { if (draftReady) saveDraft(draftKey.value, form) }, { deep: true, flush: 'sync' })

async function onSubmit() {
  if (saving.value || loading.value || loadError.value) return
  errors.value = []
  saving.value = true
  try {
    let article: Article
    if (slug.value) {
      const { title, description, body } = form
      article = await api.articles.updateArticle(slug.value, {
        article: { title, description, body },
      }).then(res => res.data.article)
    }
    else {
      article = await api.articles.createArticle({ article: form }).then(res => res.data.article)
    }
    removeDraft(draftKey.value)
    draftReady = false
    await router.push({ name: 'article', params: { slug: article.slug } })
  }
  catch (error) {
    errors.value = errorMessages(error)
    if (isFetchError(error) && error.status === 401 && !userStore.isAuthorized) {
      saveDraft(draftKey.value, form)
      await router.push({ name: 'login', query: { redirect: route.fullPath } })
    }
  }
  finally { saving.value = false }
}
</script>
