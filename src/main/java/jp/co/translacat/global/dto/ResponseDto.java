package jp.co.translacat.global.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
public class ResponseDto<T> {

        @NonNull
        private Integer resultCode;

        @NonNull
        private String message;

        @NonNull
        private T body;

        private String guid;

        @JsonIgnore
        private RequestContextDto contextVo;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Tokyo")
        private LocalDateTime createDate;
}
