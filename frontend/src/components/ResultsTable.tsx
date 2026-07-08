import type { ItineraryItem } from '../types'

interface Props {
  items: ItineraryItem[]
  loading: boolean
  hasMore: boolean
  onLoadMore: () => void
}

const timeFormatter = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' })

function formatTime(iso: string): string {
  return timeFormatter.format(new Date(iso))
}

export function ResultsTable({ items, loading, hasMore, onLoadMore }: Props) {
  if (!loading && items.length === 0) {
    return <p className="empty">No itineraries yet. Load data, then search.</p>
  }

  return (
    <div className="results">
      <table>
        <thead>
          <tr>
            <th>Route</th>
            <th>Carrier</th>
            <th>Departure</th>
            <th>Arrival</th>
            <th>Stops</th>
            <th>Source</th>
            <th className="num">Price</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item, index) => (
            <tr key={`${item.origin}-${item.destination}-${item.departureTime}-${item.carrier}-${index}`}>
              <td className="route">{item.origin} → {item.destination}</td>
              <td>{item.carrier}</td>
              <td>{formatTime(item.departureTime)}</td>
              <td>{formatTime(item.arrivalTime)}</td>
              <td>{item.stops === 0 ? 'Direct' : `${item.stops} stop${item.stops > 1 ? 's' : ''}`}</td>
              <td>
                <span className={`tag tag--${item.source.toLowerCase()}`}>{item.source}</span>
              </td>
              <td className="num price">
                {item.price.toFixed(2)} {item.currency}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="results__footer">
        {hasMore ? (
          <button onClick={onLoadMore} disabled={loading}>
            {loading ? 'Loading…' : 'Load more'}
          </button>
        ) : (
          <span className="muted">{items.length} itineraries</span>
        )}
      </div>
    </div>
  )
}
