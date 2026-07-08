import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { SearchForm } from './SearchForm'
import type { Filters } from '../types'

const EMPTY: Filters = { origin: '', destination: '', maxPrice: '', carrier: '' }

describe('SearchForm', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('debounces a live search once a full route is entered', () => {
    const onSearch = vi.fn()
    render(<SearchForm filters={EMPTY} onSearch={onSearch} disabled={false} />)

    fireEvent.change(screen.getByPlaceholderText('MAD'), { target: { value: 'mad' } })
    fireEvent.change(screen.getByPlaceholderText('LHR'), { target: { value: 'lhr' } })
    act(() => vi.advanceTimersByTime(350))

    expect(onSearch).toHaveBeenLastCalledWith({ origin: 'MAD', destination: 'LHR', maxPrice: '', carrier: '' })
  })

  it('does not search while an airport code is incomplete', () => {
    const onSearch = vi.fn()
    render(<SearchForm filters={EMPTY} onSearch={onSearch} disabled={false} />)

    fireEvent.change(screen.getByPlaceholderText('MAD'), { target: { value: 'ma' } })
    act(() => vi.advanceTimersByTime(350))

    expect(onSearch).not.toHaveBeenCalled()
  })
})
