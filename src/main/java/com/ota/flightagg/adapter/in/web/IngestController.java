package com.ota.flightagg.adapter.in.web;

import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.port.in.IngestFlightsUseCase;
import com.ota.flightagg.domain.port.out.ProviderQuery;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestFlightsUseCase ingestFlights;

    public IngestController(IngestFlightsUseCase ingestFlights) {
        this.ingestFlights = ingestFlights;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IngestionResponse ingest(@Valid @RequestBody IngestionRequest request) {
        var query = new ProviderQuery(
                new Airport(request.origin()),
                new Airport(request.destination()),
                request.date());
        return IngestionResponse.from(ingestFlights.ingest(query));
    }
}
