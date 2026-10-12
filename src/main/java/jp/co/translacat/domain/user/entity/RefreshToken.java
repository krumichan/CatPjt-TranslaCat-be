package jp.co.translacat.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jp.co.translacat.global.jpa.Base;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "refresh_token")
public class RefreshToken extends Base {
    @Id
    private Long userId;

    // 서명과 사용자 claim이 포함된 JWT는 기본 VARCHAR(255)를 넘을 수 있다.
    // 갱신 API가 허용하는 최대 길이와 DB 저장 한도를 일치시킨다.
    @Column(length = 8192)
    private String token;

    private LocalDateTime expireDate;
}
