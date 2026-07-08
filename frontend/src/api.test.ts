import { afterEach, describe, expect, it, vi } from 'vitest'
import { searchFlights } from './api'

describe('api.searchFlights', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('builds a filtered, paginated query string', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ items: [], nextCursor: null }) })
    vi.stubGlobal('fetch', fetchMock)

    await searchFlights({ origin: 'MAD', destination: 'LHR', maxPrice: '200', carrier: 'IB' }, 'CUR')

    const url = String(fetchMock.mock.calls[0][0])
    expect(url).toContain('origin=MAD')
    expect(url).toContain('destination=LHR')
    expect(url).toContain('maxPrice=200')
    expect(url).toContain('carrier=IB')
    expect(url).toContain('cursor=CUR')
    expect(url).toContain('size=20')
  })

  it('throws on a non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }))

    await expect(searchFlights({ origin: '', destination: '', maxPrice: '', carrier: '' }, null)).rejects.toThrow()
  })
})
