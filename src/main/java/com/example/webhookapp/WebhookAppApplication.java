package com.example.webhookapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class WebhookAppApplication {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public CommandLineRunner commandLineRunner(WebhookService webhookService) {
        return args -> {
            webhookService.processWebhook();
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(WebhookAppApplication.class, args);
    }
}