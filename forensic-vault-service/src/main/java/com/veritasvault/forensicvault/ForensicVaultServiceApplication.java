package com.veritasvault.forensicvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ForensicVaultServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForensicVaultServiceApplication.class, args);
    }
}
