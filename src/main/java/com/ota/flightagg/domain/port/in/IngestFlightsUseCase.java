package com.ota.flightagg.domain.port.in;

import com.ota.flightagg.domain.port.out.ProviderQuery;

public interface IngestFlightsUseCase {

    IngestionResult ingest(ProviderQuery query);
}
