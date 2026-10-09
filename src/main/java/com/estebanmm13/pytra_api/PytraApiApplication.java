package com.estebanmm13.pytra_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PytraApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PytraApiApplication.class, args);
	}

}
