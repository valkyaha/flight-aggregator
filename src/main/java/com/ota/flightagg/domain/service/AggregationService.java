package com.ota.flightagg.domain.service;

import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.ItineraryKey;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class AggregationService {

    private final MarkupPolicy markupPolicy;

    public AggregationService(MarkupPolicy markupPolicy) {
        this.markupPolicy = Objects.requireNonNull(markupPolicy, "markupPolicy must not be null");
    }

    public List<Itinerary> aggregate(Collection<Itinerary> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        var cheapestByKey = new LinkedHashMap<ItineraryKey, Itinerary>();
        for (var candidate : candidates) {
            cheapestByKey.merge(candidate.key(), candidate, AggregationService::cheaper);
        }
        return cheapestByKey.values().stream().map(this::withMarkup).toList();
    }

    private static Itinerary cheaper(Itinerary current, Itinerary candidate) {
        var byPrice = candidate.price().amount().compareTo(current.price().amount());
        if (byPrice != 0) {
            return byPrice < 0 ? candidate : current;
        }
        return candidate.source().ordinal() < current.source().ordinal() ? candidate : current;
    }

    private Itinerary withMarkup(Itinerary itinerary) {
        var marked = markupPolicy.applyTo(itinerary.price());
        return new Itinerary(itinerary.legs(), marked, itinerary.source());
    }
}
