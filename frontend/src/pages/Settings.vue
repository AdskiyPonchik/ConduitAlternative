<template>
  <div class="settings-page">
    <div class="container page">
      <div class="row">
        <div class="col-md-6 offset-md-3 col-xs-12">
          <h1 class="text-xs-center">
            Your Settings
          </h1>

          <ul class="error-messages">
            <li v-for="(error, field) in errors" :key="field">
              {{ field }} {{ error ? error[0] : '' }}
            </li>
          </ul>

          <form @submit.prevent="onSubmit">
            <fieldset>
              <fieldset class="form-group">
                <input
                  type="text"
                  class="form-control"
                  aria-label="Avatar picture url"
                  v-model="form.image"
                  placeholder="URL of profile picture"
                >
              </fieldset>
              <fieldset class="form-group">
                <input
                  type="text"
                  class="form-control form-control-lg"
                  aria-label="Username"
                  v-model="form.username"
                  placeholder="Your name"
                >
              </fieldset>
              <fieldset class="form-group">
                <textarea
                  class="form-control form-control-lg"
                  aria-label="Bio"
                  v-model="form.bio"
                  placeholder="Short bio about you"
                  :rows="8"
                />
              </fieldset>
              <fieldset class="form-group">
                <input
                  type="email"
                  class="form-control form-control-lg"
                  aria-label="Email"
                  v-model="form.email"
                  placeholder="Email"
                >
              </fieldset>
              <fieldset class="form-group">
                <input
                  type="password"
                  class="form-control form-control-lg"
                  aria-label="New password"
                  v-model="form.password"
                  placeholder="New password (15–128 characters)"
                >
              </fieldset>
              <button
                type="submit"
                class="btn btn-lg btn-primary pull-xs-right"
                :disabled="saving || isButtonDisabled"
              >
                Update Settings
              </button>
            </fieldset>
          </form>

          <hr>

          <button
            class="btn btn-outline-danger"
            aria-label="Logout"
            @click="onLogout"
          >
            Or click here to logout.
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { routerPush } from 'src/router'
import { api } from 'src/services'
import type { UpdateUser } from 'src/services/api'
import { useUserStore } from 'src/store/user'
import { validatePassword } from 'src/utils/password'
import { formErrors } from 'src/utils/errors'
import { signInAfterExpiry } from 'src/utils/use-async'

const form: UpdateUser = reactive({})

const userStore = useUserStore()
const errors = ref<Record<string, string[]>>({})
const saving = ref(false)

async function onSubmit() {
  if (saving.value) return
  errors.value = {}
  if (form.password) {
    const passwordError = validatePassword(form.password)
    if (passwordError) {
      errors.value = { password: [passwordError] }
      return
    }
  }

  saving.value = true
  try {
    const filteredForm = Object.entries(form).reduce((acc, [k, v]) => {
      if (v === null || (k === 'password' && v === '')) {
        return acc
      }
      return Object.assign(acc, { [k]: v })
    }, {})
    const userData = await api.user.updateCurrentUser({ user: filteredForm }).then(res => res.data.user)
    userStore.updateUser(userData)
    await routerPush('profile', { username: userData.username })
  }
  catch (error) {
    errors.value = formErrors(error)
    await signInAfterExpiry(error)
  }
  finally { saving.value = false }
}

async function onLogout() {
  userStore.updateUser(null)
  await routerPush('global-feed')
}

onMounted(async () => {
  if (!userStore.isAuthorized)
    return await routerPush('login')

  form.image = userStore.user?.image
  form.username = userStore.user?.username
  form.bio = userStore.user?.bio
  form.email = userStore.user?.email
  form.password = ''
})

const isButtonDisabled = computed(() =>
  form.image === userStore.user?.image
  && form.username === userStore.user?.username
  && form.bio === userStore.user?.bio
  && form.email === userStore.user?.email
  && !form.password,
)
</script>
