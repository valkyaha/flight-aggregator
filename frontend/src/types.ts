export interface ItineraryItem {
  origin: string
  destination: string
  carrier: string
  price: number
  currency: string
  departureTime: string
  arrivalTime: string
  stops: number
  source: string
}

export interface SearchResponse {
  items: ItineraryItem[]
  nextCursor: string | null
}

export interface IngestResponse {
  providersQueried: number
  providersSucceeded: number
  received: number
  rejected: number
  aggregated: number
}

export interface Filters {
  origin: string
  destination: string
  maxPrice: string
  carrier: string
}
