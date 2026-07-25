package com.univault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UnivaultApplication {

	public static void main(String[] args) {
		SpringApplication.run(UnivaultApplication.class, args);
	}

}
