package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import java.util.List;

/** 사용자 선택의 당시 revision을 저장한다. 원격 토큰/비밀키는 저장하지 않는다. */
public record SettingsSelectionPayload(Long userId, String expectedRevision, List<ListeningTaskType> taskTypes) { }
