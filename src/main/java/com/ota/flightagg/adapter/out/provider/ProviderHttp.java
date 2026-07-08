package com.ota.flightagg.adapter.out.provider;

final class ProviderHttp {

    static final String QUERY_ORIGIN = "origin";
    static final String QUERY_DESTINATION = "destination";
    static final String QUERY_DATE = "date";

    static final long RETRY_MAX_ATTEMPTS = 3;
    static final long RETRY_INITIAL_DELAY_MS = 200;
    static final double RETRY_BACKOFF_MULTIPLIER = 2.0;
    static final long RETRY_MAX_DELAY_MS = 2000;
    static final long RETRY_ATTEMPT_TIMEOUT_MS = 4000;

    private ProviderHttp() {
    }
}
