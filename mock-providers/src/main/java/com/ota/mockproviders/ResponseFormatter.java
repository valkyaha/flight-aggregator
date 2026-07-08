package com.ota.mockproviders;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class ResponseFormatter {

    private static final String CURRENCY = "EUR";
    private static final String ITINERARY_ID_PREFIX = "IT";
    private static final String CSV_HEADER = "itineraryId,carrier,flightNumber,from,to,departure,arrival,price,currency\n";

    private static final int ALPHA_PROVIDER_INDEX = 0;
    private static final int BETA_PROVIDER_INDEX = 1;
    private static final int GAMMA_PROVIDER_INDEX = 2;

    private static final int PRICE_SCALE = 2;
    private static final double NEUTRAL_FACTOR = 1.0;
    private static final int PRICE_VARIANTS = 5;
    private static final int PRICE_VARIANT_CENTER = 2;
    private static final double PRICE_VARIANT_STEP = 0.02;

    public String alphaJson(List<Offer> offers) {
        var json = new StringBuilder("[");
        for (var i = 0; i < offers.size(); i++) {
            var offer = offers.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"airline\":\"").append(offer.segments().getFirst().carrier())
                    .append("\",\"price\":\"").append(price(offer.basePrice(), i, ALPHA_PROVIDER_INDEX))
                    .append("\",\"currency\":\"").append(CURRENCY).append("\",\"segments\":[");
            for (var s = 0; s < offer.segments().size(); s++) {
                var segment = offer.segments().get(s);
                if (s > 0) {
                    json.append(',');
                }
                json.append("{\"carrier\":\"").append(segment.carrier())
                        .append("\",\"flightNumber\":\"").append(segment.flightNumber())
                        .append("\",\"from\":\"").append(segment.from())
                        .append("\",\"to\":\"").append(segment.to())
                        .append("\",\"departure\":\"").append(segment.departure())
                        .append("\",\"arrival\":\"").append(segment.arrival()).append("\"}");
            }
            json.append("]}");
        }
        return json.append(']').toString();
    }

    public String betaXml(List<Offer> offers) {
        var xml = new StringBuilder("<GdsResponse><Itineraries>");
        for (var i = 0; i < offers.size(); i++) {
            var offer = offers.get(i);
            xml.append("<Itinerary><Pricing><Amount>").append(price(offer.basePrice(), i, BETA_PROVIDER_INDEX))
                    .append("</Amount><Currency>").append(CURRENCY).append("</Currency></Pricing><Segments>");
            for (var segment : offer.segments()) {
                xml.append("<Segment><MarketingCarrier>").append(segment.carrier())
                        .append("</MarketingCarrier><FlightNumber>").append(segment.flightNumber())
                        .append("</FlightNumber><Departure><Airport>").append(segment.from())
                        .append("</Airport><DateTime>").append(segment.departure())
                        .append("</DateTime></Departure><Arrival><Airport>").append(segment.to())
                        .append("</Airport><DateTime>").append(segment.arrival())
                        .append("</DateTime></Arrival></Segment>");
            }
            xml.append("</Segments></Itinerary>");
        }
        return xml.append("</Itineraries></GdsResponse>").toString();
    }

    public String gammaCsv(List<Offer> offers) {
        var csv = new StringBuilder(CSV_HEADER);
        for (var i = 0; i < offers.size(); i++) {
            var offer = offers.get(i);
            var price = price(offer.basePrice(), i, GAMMA_PROVIDER_INDEX);
            for (var segment : offer.segments()) {
                csv.append(ITINERARY_ID_PREFIX).append(i).append(',')
                        .append(segment.carrier()).append(',')
                        .append(segment.flightNumber()).append(',')
                        .append(segment.from()).append(',')
                        .append(segment.to()).append(',')
                        .append(segment.departure()).append(',')
                        .append(segment.arrival()).append(',')
                        .append(price).append(',').append(CURRENCY).append('\n');
            }
        }
        return csv.toString();
    }

    private static String price(BigDecimal basePrice, int offerIndex, int providerIndex) {
        var variance = (offerIndex + providerIndex) % PRICE_VARIANTS - PRICE_VARIANT_CENTER;
        var factor = BigDecimal.valueOf(NEUTRAL_FACTOR + variance * PRICE_VARIANT_STEP);
        return basePrice.multiply(factor).setScale(PRICE_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
