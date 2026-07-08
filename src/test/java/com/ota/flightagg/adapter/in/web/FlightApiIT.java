package com.ota.flightagg.adapter.in.web;

import com.ota.flightagg.TestcontainersConfiguration;
import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.out.FlightRepositoryPort;
import com.ota.flightagg.domain.port.out.SearchCachePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ota.flightagg.domain.Fixtures.itinerary;
import static com.ota.flightagg.domain.Fixtures.leg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FlightApiIT {

    private static final Pattern NEXT_CURSOR = Pattern.compile("\"nextCursor\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FlightRepositoryPort repository;

    @Autowired
    private SearchCachePort cache;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        jdbcTemplate.execute("TRUNCATE itinerary RESTART IDENTITY");
        cache.invalidateAll();
    }

    private static Itinerary direct(Provider source, String carrier, String number, String destination,
                                    String price, String departure, String arrival) {
        return itinerary(source, price, leg(carrier, number, "MAD", destination, departure, arrival));
    }

    @Test
    void searchesAndPaginatesThroughTheFullStack() throws Exception {
        repository.saveAll(List.of(
                direct(Provider.ALPHA, "IB", "3170", "LHR", "300.00", "2026-07-10T10:00:00Z", "2026-07-10T12:00:00Z"),
                direct(Provider.ALPHA, "IB", "3171", "LHR", "100.00", "2026-07-10T06:00:00Z", "2026-07-10T08:00:00Z"),
                direct(Provider.ALPHA, "IB", "3172", "LHR", "200.00", "2026-07-10T08:00:00Z", "2026-07-10T10:00:00Z")));

        var first = mvc.perform(get("/api/flights").param("origin", "MAD").param("destination", "LHR").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].price").value(100.00))
                .andExpect(jsonPath("$.items[1].price").value(200.00))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        var matcher = NEXT_CURSOR.matcher(first);
        assertThat(matcher.find()).isTrue();
        var cursor = matcher.group(1);

        mvc.perform(get("/api/flights")
                        .param("origin", "MAD").param("destination", "LHR").param("size", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].price").value(300.00))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void appliesCarrierFilterThroughTheFullStack() throws Exception {
        repository.saveAll(List.of(
                direct(Provider.ALPHA, "IB", "3170", "LHR", "100.00", "2026-07-10T08:00:00Z", "2026-07-10T10:00:00Z"),
                direct(Provider.ALPHA, "BA", "500", "LHR", "120.00", "2026-07-10T09:00:00Z", "2026-07-10T11:00:00Z")));

        mvc.perform(get("/api/flights").param("carrier", "IB").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].carrier").value("IB"));
    }
}
