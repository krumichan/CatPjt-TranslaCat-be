package jp.co.translacat.domain.languagelearning.level.dto.request;

/** 得点と所有者はLLが決定し、クライアントは帯域と再送キーだけを指定する。 */
public record LevelSelfSelectRequestDto(String band, String idempotencyKey) {}
