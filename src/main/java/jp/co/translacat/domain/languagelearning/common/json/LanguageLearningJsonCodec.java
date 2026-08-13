package jp.co.translacat.domain.languagelearning.common.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LanguageLearningJsonCodec {

    private final ObjectMapper objectMapper;

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(
                    "Language Learning JSON 변환에 실패했습니다.",
                    LanguageLearningErrorCode.JSON_PROCESSING_FAILED
            );
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException(
                    "Language Learning JSON 읽기에 실패했습니다.",
                    LanguageLearningErrorCode.JSON_PROCESSING_FAILED
            );
        }
    }

    public <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException(
                    "Language Learning JSON 읽기에 실패했습니다.",
                    LanguageLearningErrorCode.JSON_PROCESSING_FAILED
            );
        }
    }
}
