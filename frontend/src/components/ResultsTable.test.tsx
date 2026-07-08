import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ResultsTable } from './ResultsTable'
import type { ItineraryItem } from '../types'

const item: ItineraryItem = {
  origin: 'MAD',
  destination: 'LHR',
  carrier: 'IB',
  price: 105,
  currency: 'EUR',
  departureTime: '2026-07-10T08:00:00Z',
  arrivalTime: '2026-07-10T10:15:00Z',
  stops: 0,
  source: 'BETA',
}

describe('ResultsTable', () => {
  it('renders itinerary rows', () => {
    render(<ResultsTable items={[item]} loading={false} hasMore={false} onLoadMore={() => {}} />)

    expect(screen.getByText('MAD → LHR')).toBeInTheDocument()
    expect(screen.getByText('BETA')).toBeInTheDocument()
    expect(screen.getByText(/105\.00 EUR/)).toBeInTheDocument()
  })

  it('shows an empty state when there are no results', () => {
    render(<ResultsTable items={[]} loading={false} hasMore={false} onLoadMore={() => {}} />)

    expect(screen.getByText(/No itineraries/)).toBeInTheDocument()
  })
})
