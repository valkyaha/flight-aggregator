import { useCallback, useEffect, useState } from 'react'
import { searchFlights } from './api'
import type { Filters, ItineraryItem } from './types'
import { SearchForm } from './components/SearchForm'
import { ResultsTable } from './components/ResultsTable'
import { IngestPanel } from './components/IngestPanel'

const EMPTY_FILTERS: Filters = { origin: '', destination: '', maxPrice: '', carrier: '' }

export default function App() {
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [items, setItems] = useState<ItineraryItem[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const runSearch = useCallback(async (activeFilters: Filters) => {
    setLoading(true)
    setError(null)
    try {
      const page = await searchFlights(activeFilters, null)
      setItems(page.items)
      setNextCursor(page.nextCursor)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unexpected error')
      setItems([])
      setNextCursor(null)
    } finally {
      setLoading(false)
    }
  }, [])

  const loadMore = useCallback(async () => {
    if (!nextCursor) {
      return
    }
    setLoading(true)
    setError(null)
    try {
      const page = await searchFlights(filters, nextCursor)
      setItems((current) => [...current, ...page.items])
      setNextCursor(page.nextCursor)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unexpected error')
    } finally {
      setLoading(false)
    }
  }, [filters, nextCursor])

  useEffect(() => {
    void runSearch(EMPTY_FILTERS)
  }, [runSearch])

  const handleSearch = useCallback((next: Filters) => {
    setFilters(next)
    void runSearch(next)
  }, [runSearch])

  return (
    <div className="app">
      <header className="app__header">
        <h1>Flight Aggregator</h1>
        <p>Deduplicated GDS itineraries · cheapest fare · 5% OTA markup</p>
      </header>
      <main className="app__main">
        <section className="panel">
          <h2>Ingest</h2>
          <IngestPanel onIngested={() => void runSearch(filters)} />
        </section>
        <section className="panel">
          <h2>Search</h2>
          <SearchForm filters={filters} onSearch={handleSearch} disabled={loading} />
        </section>
        {error && <p className="error">{error}</p>}
        <ResultsTable items={items} loading={loading} hasMore={nextCursor !== null} onLoadMore={() => void loadMore()} />
      </main>
    </div>
  )
}
