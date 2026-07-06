package com.ota.flightagg;

import org.springframework.boot.SpringApplication;

public class TestFlightAggregatorApplication {

	public static void main(String[] args) {
		SpringApplication.from(FlightAggregatorApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
