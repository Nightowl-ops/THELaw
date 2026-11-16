package com.veritasvault.caseevidence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CaseEvidenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaseEvidenceServiceApplication.class, args);
    }
}
