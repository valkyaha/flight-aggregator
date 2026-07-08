import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import type { Filters } from '../types'

interface Props {
  filters: Filters
  onSearch: (filters: Filters) => void
  disabled: boolean
}

const IATA_CODE_LENGTH = 3
const CARRIER_CODE_LENGTH = 2
const SEARCH_DEBOUNCE_MS = 300

function isSearchable(filters: Filters): boolean {
  return (filters.origin === '' || filters.origin.length === IATA_CODE_LENGTH)
    && (filters.destination === '' || filters.destination.length === IATA_CODE_LENGTH)
    && (filters.carrier === '' || filters.carrier.length === CARRIER_CODE_LENGTH)
}

export function SearchForm({ filters, onSearch, disabled }: Props) {
  const [form, setForm] = useState<Filters>(filters)
  const firstRender = useRef(true)

  const update = (field: keyof Filters) => (event: ChangeEvent<HTMLInputElement>) => {
    const value = field === 'maxPrice' ? event.target.value : event.target.value.toUpperCase()
    setForm((current) => ({ ...current, [field]: value }))
  }

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    if (!isSearchable(form)) {
      return
    }
    const handle = setTimeout(() => onSearch(form), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(handle)
  }, [form, onSearch])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSearch(form)
  }

  return (
    <form className="filters" onSubmit={submit}>
      <label>
        Origin
        <input value={form.origin} onChange={update('origin')} placeholder="MAD" maxLength={IATA_CODE_LENGTH} />
      </label>
      <label>
        Destination
        <input value={form.destination} onChange={update('destination')} placeholder="LHR" maxLength={IATA_CODE_LENGTH} />
      </label>
      <label>
        Carrier
        <input value={form.carrier} onChange={update('carrier')} placeholder="IB" maxLength={CARRIER_CODE_LENGTH} />
      </label>
      <label>
        Max price
        <input value={form.maxPrice} onChange={update('maxPrice')} placeholder="500" inputMode="decimal" />
      </label>
      <button type="submit" disabled={disabled}>
        Search
      </button>
    </form>
  )
}
