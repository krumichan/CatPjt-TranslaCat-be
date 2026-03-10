package jp.co.translacat.domain.user.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.enums.Role;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "user")
@NoArgsConstructor
public class User extends BaseAuditable {

    @Builder(access = AccessLevel.PRIVATE)
    private User(String email, String password, String username, String socialId, SocialType socialType, Role authority) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.socialType = socialType;
        this.socialId = socialId;
        this.authority = authority;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column
    private String password;

    @Column
    private String username;

    @Enumerated(EnumType.STRING)
    private SocialType socialType;

    @Column
    private String socialId;

    @Enumerated(EnumType.STRING)
    private Role authority;

    public static User createLocalUser(String email, String password, String username, Role authority) {
        return User.builder()
                .email(email)
                .password(password)
                .username(username)
                .socialType(SocialType.LOCAL)
                .authority(authority)
                .build();
    }

    public static User createSocialUser(String email, String username, SocialType socialType,  String socialId, Role authority) {
        return User.builder()
                .email(email)
                .username(username)
                .socialType(socialType)
                .socialId(socialId)
                .authority(authority)
                .build();
    }
}
