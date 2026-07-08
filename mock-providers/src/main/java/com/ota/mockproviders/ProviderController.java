package com.ota.mockproviders;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
public class ProviderController {

    private final FlightGenerator generator;
    private final ResponseFormatter formatter;
    private final MockProperties properties;

    public ProviderController(FlightGenerator generator, ResponseFormatter formatter, MockProperties properties) {
        this.generator = generator;
        this.formatter = formatter;
        this.properties = properties;
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @GetMapping(value = "/alpha/flights", produces = MediaType.APPLICATION_JSON_VALUE)
    public String alpha(@RequestParam(required = false) String origin,
                        @RequestParam(required = false) String destination,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return formatter.alphaJson(offers(origin, destination, date));
    }

    @GetMapping(value = "/beta/flights", produces = MediaType.APPLICATION_XML_VALUE)
    public String beta(@RequestParam(required = false) String origin,
                       @RequestParam(required = false) String destination,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return formatter.betaXml(offers(origin, destination, date));
    }

    @GetMapping(value = "/gamma/flights", produces = "text/csv")
    public String gamma(@RequestParam(required = false) String origin,
                        @RequestParam(required = false) String destination,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return formatter.gammaCsv(offers(origin, destination, date));
    }

    private List<Offer> offers(String origin, String destination, LocalDate date) {
        return generator.generate(
                origin == null ? "MAD" : origin,
                destination == null ? "LHR" : destination,
                date == null ? LocalDate.now(ZoneOffset.UTC) : date,
                properties.itinerariesPerQuery());
    }
}
