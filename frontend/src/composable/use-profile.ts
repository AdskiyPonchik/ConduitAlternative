import type { ComputedRef } from 'vue'
import { api } from 'src/services'
import type { Profile } from 'src/services/api'
import { useUserStore } from 'src/store/user'
import { useResource } from './use-resource'

export function useProfile({ username }: { username: ComputedRef<string> }) {
  const user = useUserStore()
  const resource = useResource<Profile | null>(
    () => [username.value, user.user?.token],
    async () => username.value
      ? (await api.profiles.getProfileByUsername(encodeURIComponent(username.value))).data.profile : null,
    null,
  )
  return { profile: resource.data, error: resource.error, loading: resource.loading,
    fetchProfile: resource.reload, updateProfile: (profile: Profile | null) => { resource.data.value = profile } }
}
