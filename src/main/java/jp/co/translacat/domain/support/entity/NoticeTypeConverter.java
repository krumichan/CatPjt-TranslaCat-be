package jp.co.translacat.domain.support.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import jp.co.translacat.domain.support.enums.NoticeType;

@Converter
public class NoticeTypeConverter implements AttributeConverter<NoticeType, Boolean> {

    @Override
    public Boolean convertToDatabaseColumn(NoticeType noticeType) {
        // 기존 important 컬럼을 유지하여 이미 저장된 공지를 데이터 재작성 없이 사용한다.
        return noticeType == null ? null : noticeType == NoticeType.URGENT;
    }

    @Override
    public NoticeType convertToEntityAttribute(Boolean important) {
        if (important == null) {
            return null;
        }

        return important ? NoticeType.URGENT : NoticeType.NORMAL;
    }
}
