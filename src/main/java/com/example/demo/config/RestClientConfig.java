package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient zippopotamRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.zippopotam.us")
                .build();
    }
}
