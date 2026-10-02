import '@testing-library/jest-dom/vitest'
import { Storage } from 'happy-dom'

// Node 25+ defines its own storage globals, which Vitest 3 does not replace.
// Keep browser tests isolated in Happy DOM's in-memory storage.
Object.defineProperty(globalThis, 'localStorage', { value: new Storage(), configurable: true })
Object.defineProperty(globalThis, 'sessionStorage', { value: new Storage(), configurable: true })

// https://github.com/mswjs/msw/issues/1415#issuecomment-1650562700
location.href = 'https://localhost:5000/'
