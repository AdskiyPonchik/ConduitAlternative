import { effectScope, nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useResource } from './use-resource'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

describe('resource requests', () => {
  it('ignores an older response and its loading state while the latest request is pending', async () => {
    const old = deferred<string>()
    const latest = deferred<string>()
    const key = ref(0)
    const load = vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)
    const scope = effectScope()
    const resource = scope.run(() => useResource(key, load, ''))!
    key.value++
    await nextTick()
    old.resolve('old')
    await old.promise
    expect(resource.data.value).toBe('')
    expect(resource.loading.value).toBe(true)
    latest.resolve('latest')
    await latest.promise
    expect(resource.data.value).toBe('latest')
    expect(resource.loading.value).toBe(false)
    scope.stop()
  })

  it('ignores stale failures, shows current failures, and can retry', async () => {
    const old = deferred<string>()
    const key = ref(0)
    const load = vi.fn().mockReturnValueOnce(old.promise).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce('recovered')
    const scope = effectScope()
    const resource = scope.run(() => useResource(key, load, ''))!
    key.value++
    await nextTick()
    await Promise.resolve()
    old.reject(new Error('old failure'))
    await old.promise.catch(() => {})
    expect(resource.error.value).toContain('Unable to complete')
    await resource.reload()
    expect(resource.data.value).toBe('recovered')
    expect(resource.error.value).toBe('')
    scope.stop()
  })

  it('does not apply responses after the component scope is disposed', async () => {
    const pending = deferred<string>()
    const scope = effectScope()
    const resource = scope.run(() => useResource(() => 'article', () => pending.promise, ''))!
    scope.stop()
    pending.resolve('unmounted')
    await pending.promise
    expect(resource.data.value).toBe('')
  })
})
