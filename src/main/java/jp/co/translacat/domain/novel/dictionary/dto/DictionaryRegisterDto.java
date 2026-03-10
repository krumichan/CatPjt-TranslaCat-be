package jp.co.translacat.domain.novel.dictionary.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class DictionaryRegisterDto {

    @NotBlank
    private String surface;

    @NotBlank
    private String reading;
}
