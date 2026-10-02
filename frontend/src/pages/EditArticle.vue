<template>
  <div class="editor-page">
    <div class="container page">
      <div class="row">
        <div class="col-md-10 offset-md-1 col-xs-12">
          <ul v-if="errors.length" class="error-messages">
            <li v-for="error in errors" :key="error">{{ error }}</li>
          </ul>
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
                  <label><strong>Artikel-Inhalt (Markdown)</strong></label>
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
                  <label><strong>Live-Vorschau (WYSIWYG)</strong></label>
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
              :disabled="saving || !(form.title && form.description && form.body)"
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, isFetchError } from 'src/services'
import type { Article } from 'src/services/api'
import renderMarkdown from 'src/plugins/marked'
import { useUserStore } from 'src/store/user'

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

async function fetchArticle(slug: string) {
  const article = await api.articles.getArticle(slug).then(res => res.data.article)

  form.title = article.title
  form.description = article.description
  form.body = article.body
  form.tagList = article.tagList


}

onMounted(async () => {
  if (!userStore.isAuthorized) {
    await router.replace({ name: 'login' })
    return
  }
  if (slug.value) {
    try { await fetchArticle(slug.value) }
    catch (error) { showErrors(error) }
  }
})

function showErrors(error: unknown) {
  errors.value = isFetchError(error)
    ? Object.values(error.error?.errors ?? {}).flat()
    : ['Unable to save the article. Please try again.']
  if (!errors.value.length) errors.value = ['Unable to save the article. Please try again.']
}

async function onSubmit() {
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
    await router.push({ name: 'article', params: { slug: article.slug } })
  }
  catch (error) { showErrors(error) }
  finally { saving.value = false }
}
</script>
