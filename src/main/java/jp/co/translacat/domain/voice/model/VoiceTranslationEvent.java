package jp.co.translacat.domain.voice.model;

import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoiceTranslationEvent {
    private final String groupId;
    private final TranslationUnit unit;
    private final String userEmail;
}
