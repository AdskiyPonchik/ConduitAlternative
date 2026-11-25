import { isRef } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import useAsync from 'src/utils/use-async'

const someProcess = (): Promise<null> => Promise.resolve(null)

describe('# Create async process', () => {
  it('ignores repeated submissions until the current action completes', async () => {
    let finish!: (value: number) => void
    const action = vi.fn(() => new Promise<number>(resolve => { finish = resolve }))
    const { run, active } = useAsync(action)
    const first = run()
    await run()
    expect(action).toHaveBeenCalledTimes(1)
    finish(42)
    expect(await first).toBe(42)
    expect(active.value).toBe(false)
  })

  it('turns network errors into visible state and allows retry', async () => {
    const action = vi.fn().mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce('ok')
    const { run, error, active } = useAsync(action)
    await run()
    expect(error.value).toContain('Unable to complete')
    expect(active.value).toBe(false)
    await expect(run()).resolves.toBe('ok')
    expect(error.value).toBe('')
  })
  it('should expect active as Vue Ref type', () => {
    const { active } = useAsync(someProcess)

    expect(isRef(active)).toBe(true)
  })

  it('should correctly test active functionality', async () => {
    const { active, run } = useAsync(someProcess)

    expect(active.value).toBe(false)

    const promise = run()

    expect(active.value).toBe(true)

    await promise

    expect(active.value).toBe(false)
  })

  it('should expect run as a function', () => {
    const { run } = useAsync(someProcess)

    expect(run).toBeInstanceOf(Function)
  })

  it('should expect original function called with correct params and return correct data', async () => {
    const someProcess = vi.fn().mockResolvedValue({ a: 1, b: null })
    const { run } = useAsync(someProcess)

    const result = await run(null)
    expect(result).toEqual({ a: 1, b: null })
    expect(someProcess).toBeCalledWith(null)
  })
})
