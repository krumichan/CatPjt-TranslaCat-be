package jp.co.translacat.infrastructure.languagelearning.client.dto;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;

import java.util.List;

/**
 * Core outbox 행 ID와 당시 관찰한 Settings revision을 전달한다.
 */
public record SelectionDeliveryRequestDto(long eventId, String expectedRevision, List<ListeningTaskType> taskTypes) {
}
