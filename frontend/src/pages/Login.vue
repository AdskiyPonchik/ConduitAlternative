<template>
  <div class="auth-page">
    <div class="container page">
      <div class="row">
        <div class="col-md-6 offset-md-3 col-xs-12">
          <h1 class="text-xs-center">
            Sign in
          </h1>
          <p class="text-xs-center">
            <AppLink name="register">
              Need an account?
            </AppLink>
          </p>
          <ul class="error-messages">
            <li
              v-for="(error, field) in errors"
              :key="field"
            >
              {{ field }} {{ error ? error[0] : '' }}
            </li>
          </ul>
          <form
            ref="formRef"
            aria-label="Login form"
            @submit.prevent="login"
          >
            <fieldset
              class="form-group"
              aria-required="true"
            >
              <input
                type="email"
                class="form-control form-control-lg"
                aria-label="Email"
                v-model="form.email"
                required
                placeholder="Email"
              >
            </fieldset>
            <fieldset class=" form-group">
              <input
                type="password"
                class="form-control form-control-lg"
                aria-label="Password"
                v-model="form.password"
                required
                placeholder="Password"
              >
            </fieldset>
            <button
              type="submit"
              class="btn btn-lg btn-primary pull-xs-right"
              :disabled="saving || !form.email || !form.password"
            >
              Sign in
            </button>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formErrors } from 'src/utils/errors'
import { api, isFetchError } from 'src/services'
import type { LoginUser } from 'src/services/api'
import { useUserStore } from 'src/store/user'

const formRef = ref<HTMLFormElement | null>(null)
const form: LoginUser = reactive({
  email: '',
  password: '',
})

const { updateUser } = useUserStore()

const errors = ref<Record<string, string[]>>({})
const saving = ref(false)
const route = useRoute()
const router = useRouter()

async function login() {
  if (saving.value) return
  errors.value = {}

  if (!formRef.value?.checkValidity())
    return

  saving.value = true
  try {
    const result = await api.users.login({ user: form }, { secure: false })
    updateUser(result.data.user)
    const target = route.query.redirect
    await router.push(typeof target === 'string' && /^\/(?!\/)/.test(target) ? target : { name: 'global-feed' })
  }
  catch (error) {
    errors.value = isFetchError(error) && error.status === 401
      ? { '': ['Incorrect email or password.'] } : formErrors(error)
  }
  finally { saving.value = false }
}
</script>
