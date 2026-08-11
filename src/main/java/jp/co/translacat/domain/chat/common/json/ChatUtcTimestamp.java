package jp.co.translacat.domain.chat.common.json;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks externally exposed chat timestamps that must include UTC information.
 */
@Target({
        ElementType.RECORD_COMPONENT,
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER
})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = ChatUtcLocalDateTimeSerializer.class)
public @interface ChatUtcTimestamp {
}
