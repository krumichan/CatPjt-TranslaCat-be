package jp.co.translacat.infrastructure.client.ai.gemini.dto;

import com.google.genai.types.Candidate;

import java.util.List;

public record AiGeminiResponseDto(List<Candidate> candidates) {
    public record Candidate(Content content) {}
    public record Content(List<Part> parts) {}
    public record Part(String text) {}

    public String getText() {
        return candidates.getFirst().content().parts().getFirst().text();
    }
}
