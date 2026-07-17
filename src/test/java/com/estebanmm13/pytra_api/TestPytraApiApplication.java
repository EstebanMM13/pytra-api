package com.estebanmm13.pytra_api;

import org.springframework.boot.SpringApplication;

public class TestPytraApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(PytraApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
