package com.veritasvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.veritasvault")
public class LawApplication {
	public static void main(String[] args) {
		SpringApplication.run(LawApplication.class, args);
	}
}