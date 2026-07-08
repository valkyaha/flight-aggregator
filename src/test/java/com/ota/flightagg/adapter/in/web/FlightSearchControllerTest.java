package com.ota.flightagg.adapter.in.web;

import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.ItineraryView;
import com.ota.flightagg.domain.model.Money;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.in.SearchFlightsUseCase;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.port.out.SearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlightSearchController.class)
class FlightSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SearchFlightsUseCase searchFlights;

    @Test
    void returnsMappedResultsWithIsoTimesAndNextCursor() throws Exception {
        var view = new ItineraryView(new Airport("MAD"), new Airport("LHR"), new Carrier("IB"),
                Money.of("105.00", "EUR"),
                Instant.parse("2026-07-10T08:00:00Z"), Instant.parse("2026-07-10T10:15:00Z"),
                0, Provider.BETA);
        given(searchFlights.search(any())).willReturn(new SearchPage(List.of(view), "CURSOR123"));

        mvc.perform(get("/api/flights").param("origin", "MAD").param("destination", "LHR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].origin").value("MAD"))
                .andExpect(jsonPath("$.items[0].carrier").value("IB"))
                .andExpect(jsonPath("$.items[0].price").value(105.00))
                .andExpect(jsonPath("$.items[0].currency").value("EUR"))
                .andExpect(jsonPath("$.items[0].departureTime").value("2026-07-10T08:00:00Z"))
                .andExpect(jsonPath("$.items[0].source").value("BETA"))
                .andExpect(jsonPath("$.nextCursor").value("CURSOR123"));
    }

    @Test
    void mapsFilterParametersOntoCriteria() throws Exception {
        given(searchFlights.search(any())).willReturn(new SearchPage(List.of(), null));

        mvc.perform(get("/api/flights")
                        .param("origin", "mad").param("destination", "lhr")
                        .param("carrier", "ib").param("maxPrice", "200").param("size", "5"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(SearchCriteria.class);
        verify(searchFlights).search(captor.capture());
        var criteria = captor.getValue();
        assertThat(criteria.origin().iata()).isEqualTo("MAD");
        assertThat(criteria.carrier().iata()).isEqualTo("IB");
        assertThat(criteria.maxPrice()).isEqualByComparingTo("200");
        assertThat(criteria.limit()).isEqualTo(5);
    }

    @Test
    void cappsPageSizeAtMaximum() throws Exception {
        given(searchFlights.search(any())).willReturn(new SearchPage(List.of(), null));

        mvc.perform(get("/api/flights").param("size", "10000")).andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(SearchCriteria.class);
        verify(searchFlights).search(captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(100);
    }

    @Test
    void rejectsInvalidCarrierWithBadRequest() throws Exception {
        mvc.perform(get("/api/flights").param("carrier", "TOOLONG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(searchFlights);
    }

    @Test
    void rejectsMalformedCursorWithBadRequest() throws Exception {
        mvc.perform(get("/api/flights").param("cursor", "!!!not-base64!!!"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchFlights);
    }
}
