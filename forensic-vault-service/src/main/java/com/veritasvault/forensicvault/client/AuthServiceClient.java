package com.veritasvault.forensicvault.client;

import com.veritasvault.forensicvault.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class AuthServiceClient {

    private final RestClient restClient;

    public AuthServiceClient(@Value("${services.auth.url:http://localhost:8081}") String authServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    public UserSummaryDto getUserById(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return restClient.get()
                    .uri("/api/users/{userId}", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ResourceNotFoundException("User not found with ID: " + userId + " in Auth Service");
                    })
                    .body(UserSummaryDto.class);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Auth Service unreachable at the moment ({}), bypassing user lookup: {}", userId, e.getMessage());
            return null;
        }
    }
}
