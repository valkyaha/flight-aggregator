package com.ota.flightagg.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record IngestionRequest(@NotBlank String origin, @NotBlank String destination, @NotNull LocalDate date) {
}
