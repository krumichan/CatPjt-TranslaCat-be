package jp.co.translacat.domain.novel.dictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class DictionaryRegisterDto {

    @NotBlank
    @Size(max = 20)
    private String surface;

    @NotBlank
    @Size(max = 20)
    private String reading;
}
