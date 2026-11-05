package com.estebanmm13.pytra_api;

import java.time.ZoneOffset;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PytraApiApplication {

	public static void main(String[] args) {
		// Every LocalDateTime.now() and stored TIMESTAMP is UTC, whatever the host's zone (the API sends them with a Z).
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
		SpringApplication.run(PytraApiApplication.class, args);
	}

}
