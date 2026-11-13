package com.example.backend.global.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

@Configuration
public class ClaudeConfig {

    @Bean
    @Lazy
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(Duration.ofSeconds(20))
                .maxRetries(2)
                .build();
    }
}
