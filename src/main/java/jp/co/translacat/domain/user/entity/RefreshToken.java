package jp.co.translacat.domain.user.entity;

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

    private String token;

    private LocalDateTime expireDate;
}
