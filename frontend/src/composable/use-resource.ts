import { onScopeDispose, ref, shallowRef, watch } from 'vue'
import type { WatchSource } from 'vue'
import { errorMessages } from 'src/utils/errors'

// Only the latest request may update the screen, including its loading/error state.
export function useResource<T>(source: WatchSource, load: () => Promise<T>, initial: T) {
  const data = shallowRef<T>(initial)
  const loading = ref(false)
  const error = ref('')
  let generation = 0

  async function reload() {
    const current = ++generation
    loading.value = true
    error.value = ''
    data.value = initial
    try {
      const result = await load()
      if (current === generation) data.value = result
    }
    catch (cause) {
      if (current === generation) error.value = errorMessages(cause).join(' ')
    }
    finally {
      if (current === generation) loading.value = false
    }
  }

  watch(source, () => { void reload() }, { immediate: true })
  onScopeDispose(() => { generation++ })
  return { data, loading, error, reload }
}
