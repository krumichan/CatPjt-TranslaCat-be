package jp.co.translacat.domain.chat.friend.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ReqUpdateProfileDto {

    private String nickname;

    private String comment;

    private MultipartFile icon;

    private MultipartFile background;
}
