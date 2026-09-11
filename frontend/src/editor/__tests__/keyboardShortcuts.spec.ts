import { describe, expect, it } from 'vitest'
import { isEditableTarget } from '../keyboardShortcuts'

describe('keyboardShortcuts.isEditableTarget', () => {
  it('es true para un <input>', () => {
    expect(isEditableTarget(document.createElement('input'))).toBe(true)
  })

  it('es true para un <textarea>', () => {
    expect(isEditableTarget(document.createElement('textarea'))).toBe(true)
  })

  it('es false para otros elementos (ej. <button>, <div>)', () => {
    expect(isEditableTarget(document.createElement('button'))).toBe(false)
    expect(isEditableTarget(document.createElement('div'))).toBe(false)
  })

  it('es false para null', () => {
    expect(isEditableTarget(null)).toBe(false)
  })
})
