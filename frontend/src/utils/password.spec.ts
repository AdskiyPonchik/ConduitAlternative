import { describe, expect, it } from 'vitest'
import { validatePassword } from './password'

describe('Java password policy', () => {
  it('requires 15 to 128 Unicode code points, including both boundaries', () => {
    expect(validatePassword('a'.repeat(14))).toMatch(/15/)
    expect(validatePassword('a'.repeat(15))).toBeUndefined()
    expect(validatePassword('a'.repeat(128))).toBeUndefined()
    expect(validatePassword('a'.repeat(129))).toMatch(/128/)
  })

  it('counts supplementary characters once rather than as UTF-16 pairs', () => {
    expect(validatePassword('😀'.repeat(14))).toMatch(/15/)
    expect(validatePassword('😀'.repeat(15))).toBeUndefined()
    expect(validatePassword('😀'.repeat(128))).toBeUndefined()
    expect(validatePassword('😀'.repeat(129))).toMatch(/128/)
  })

  it('rejects blank passwords but preserves meaningful leading and trailing spaces', () => {
    expect(validatePassword('')).toBeDefined()
    expect(validatePassword(' '.repeat(15))).toBeDefined()
    expect(validatePassword(` ${'a'.repeat(13)} `)).toBeUndefined()
  })
})
