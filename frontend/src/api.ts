import type { Filters, IngestResponse, SearchResponse } from './types'

const PAGE_SIZE = 20

export async function searchFlights(filters: Filters, cursor: string | null): Promise<SearchResponse> {
  const params = new URLSearchParams()
  if (filters.origin) params.set('origin', filters.origin)
  if (filters.destination) params.set('destination', filters.destination)
  if (filters.maxPrice) params.set('maxPrice', filters.maxPrice)
  if (filters.carrier) params.set('carrier', filters.carrier)
  if (cursor) params.set('cursor', cursor)
  params.set('size', String(PAGE_SIZE))

  const response = await fetch(`/api/flights?${params.toString()}`)
  if (!response.ok) {
    throw new Error(`Search failed (${response.status})`)
  }
  return (await response.json()) as SearchResponse
}

export async function ingest(origin: string, destination: string, date: string): Promise<IngestResponse> {
  const response = await fetch('/api/ingest', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ origin, destination, date }),
  })
  if (!response.ok) {
    throw new Error(`Ingestion failed (${response.status})`)
  }
  return (await response.json()) as IngestResponse
}
