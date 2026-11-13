package com.example.backend.global.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ClaudeClient {

    private static final long MAX_OUTPUT_TOKENS = 512L;

    private final AnthropicClient anthropicClient;
    private final String model;

    public ClaudeClient(
            @Lazy AnthropicClient anthropicClient,
            @Value("${anthropic.model}") String model
    ) {
        this.anthropicClient = anthropicClient;
        this.model = model;
    }

    public String analyze(String systemPrompt, String historyJson) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .thinking(ThinkingConfigDisabled.builder().build())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .build())
                .system(systemPrompt)
                .addUserMessage(historyJson)
                .build();

        Message message = anthropicClient.messages().create(params);

        String analysis = message.content().stream()
                .filter(content -> content.isText())
                .map(content -> content.asText().text())
                .collect(Collectors.joining("\n"))
                .strip();

        if (analysis.isBlank()) {
            String stopReason = message.stopReason()
                    .map(reason -> reason.asString())
                    .orElse("unknown");
            String contentTypes = message.content().stream()
                    .map(content -> content.type().toString())
                    .collect(Collectors.joining(","));

            throw new IllegalStateException(
                    "Claude가 빈 분석 결과를 반환했습니다. stopReason="
                            + stopReason
                            + ", contentTypes="
                            + contentTypes
            );
        }

        return analysis;
    }

}
