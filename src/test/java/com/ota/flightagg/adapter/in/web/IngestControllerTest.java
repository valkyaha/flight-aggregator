package com.ota.flightagg.adapter.in.web;

import com.ota.flightagg.domain.port.in.IngestFlightsUseCase;
import com.ota.flightagg.domain.port.in.IngestionResult;
import com.ota.flightagg.domain.port.out.ProviderQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestController.class)
class IngestControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IngestFlightsUseCase ingestFlights;

    @Test
    void triggersIngestionAndReturnsSummary() throws Exception {
        given(ingestFlights.ingest(any())).willReturn(new IngestionResult(3, 3, 10, 2, 8));

        mvc.perform(post("/api/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"MAD\",\"destination\":\"LHR\",\"date\":\"2026-07-10\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.providersSucceeded").value(3))
                .andExpect(jsonPath("$.aggregated").value(8));

        var captor = org.mockito.ArgumentCaptor.forClass(ProviderQuery.class);
        verify(ingestFlights).ingest(captor.capture());
        var query = captor.getValue();
        assertThat(query.origin().iata()).isEqualTo("MAD");
        assertThat(query.destination().iata()).isEqualTo("LHR");
        assertThat(query.departureDate()).hasToString("2026-07-10");
    }

    @Test
    void rejectsInvalidAirportWithBadRequest() throws Exception {
        mvc.perform(post("/api/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"MADRID\",\"destination\":\"LHR\",\"date\":\"2026-07-10\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingFieldWithBadRequest() throws Exception {
        mvc.perform(post("/api/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"LHR\",\"date\":\"2026-07-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void returnsCleanServerErrorWithoutLeakingDetailsWhenIngestionFails() throws Exception {
        given(ingestFlights.ingest(any())).willThrow(new RuntimeException("database connection refused"));

        mvc.perform(post("/api/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"MAD\",\"destination\":\"LHR\",\"date\":\"2026-07-10\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("an unexpected error occurred"));
    }
}
