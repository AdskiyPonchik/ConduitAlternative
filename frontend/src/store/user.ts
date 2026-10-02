import { computed, onScopeDispose, ref } from 'vue'
import { defineStore } from 'pinia'
import type { User } from 'src/services/api'
import { getSession, onSessionChange, setSession } from 'src/services/session'

export { userStorage } from 'src/services/session'
export const isAuthorized = (): boolean => !!getSession()

export const useUserStore = defineStore('user', () => {
  const user = ref(getSession())
  const isAuthorized = computed(() => !!user.value)
  const unsubscribe = onSessionChange(value => { user.value = value })
  onScopeDispose(unsubscribe)

  function updateUser(userData?: User | null) {
    setSession(userData ?? null)
  }

  return { user, isAuthorized, updateUser }
})
