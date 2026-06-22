package jp.co.translacat.domain.chat.translation.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TemporaryChatTranslationClient implements ChatTranslationClient {

    @Override
    public String translate(
            String originalText,
            String targetLanguageCode
    ) {
        /*
         * TODO:
         *  실제 AI 번역 API 연동 시 아래 임시 구현을 교체한다.
         *
         *  예상 처리:
         *  1. AI 서버 또는 번역 API에 originalText, targetLanguageCode 전달
         *  2. 번역 결과 수신
         *  3. 실패 시 예외 발생
         */
        log.debug(
                "Temporary chat translation client called. targetLanguageCode={}",
                targetLanguageCode
        );

        return "[translated:" + targetLanguageCode + "] " + originalText;
    }
}