package jp.co.translacat.infrastructure.client.ai.gemini.dto;

import java.util.List;

public record AiGeminiRequestDto(
        List<Content> contents,
        SystemInstruction system_instruction,
        GenerationConfig generationConfig) {
    public record Content(List<Part> parts) {}
    public record SystemInstruction(List<Part> parts) {}
    public record Part(String text) {}
    public record GenerationConfig(
            String response_mime_type,
            Object response_schema
    ) {}

    public static AiGeminiRequestDto of(String rule, String data, Object schema) {
        return new AiGeminiRequestDto(
                List.of(new Content(List.of(new Part(data)))),
                new SystemInstruction(List.of(new Part(rule))),
                new GenerationConfig("application/json", schema)
        );
    }
}
