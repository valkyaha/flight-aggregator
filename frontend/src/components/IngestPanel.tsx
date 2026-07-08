import { useState, type FormEvent } from 'react'
import { ingest } from '../api'
import type { IngestResponse } from '../types'

interface Props {
  onIngested: () => void
}

export function IngestPanel({ onIngested }: Props) {
  const [origin, setOrigin] = useState('MAD')
  const [destination, setDestination] = useState('LHR')
  const [date, setDate] = useState('2026-07-10')
  const [busy, setBusy] = useState(false)
  const [summary, setSummary] = useState<IngestResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const result = await ingest(origin.toUpperCase(), destination.toUpperCase(), date)
      setSummary(result)
      onIngested()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unexpected error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="ingest" onSubmit={submit}>
      <div className="ingest__fields">
        <label>
          Origin
          <input value={origin} onChange={(e) => setOrigin(e.target.value.toUpperCase())} maxLength={3} />
        </label>
        <label>
          Destination
          <input value={destination} onChange={(e) => setDestination(e.target.value.toUpperCase())} maxLength={3} />
        </label>
        <label>
          Date
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? 'Ingesting…' : 'Load data'}
        </button>
      </div>
      {summary && (
        <p className="ingest__summary">
          Providers {summary.providersSucceeded}/{summary.providersQueried} · received {summary.received} · rejected{' '}
          {summary.rejected} · aggregated {summary.aggregated}
        </p>
      )}
      {error && <p className="error">{error}</p>}
    </form>
  )
}
