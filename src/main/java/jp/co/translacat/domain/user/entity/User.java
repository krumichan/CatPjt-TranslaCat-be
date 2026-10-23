package jp.co.translacat.domain.user.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.*;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "user", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_google_identity", columnNames = {"social_type", "social_id"}))
@NoArgsConstructor
public class User extends BaseAuditable {

    @Builder(access = AccessLevel.PRIVATE)
    private User(String email, String password, String username, String socialId, SocialType socialType, Role authority,
                 String publicId) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.socialType = socialType;
        this.socialId = socialId;
        this.authority = authority;
        this.publicId = publicId;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column
    @JsonIgnore
    private String password;

    @Column(name = "birth_date")
    @JsonIgnore
    private LocalDate birthDate;

    @Column(name = "email_verified_at")
    @JsonIgnore
    private Instant emailVerifiedAt;

    @Column
    private String username;

    @Enumerated(EnumType.STRING)
    private SocialType socialType;

    @Column
    private String socialId;

    @Enumerated(EnumType.STRING)
    private Role authority;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 20)
    private String publicId;

    @Column(name = "account_state", nullable = false, length = 24)
    @JsonIgnore
    private String accountState = "ACTIVE";

    @Column(name = "deletion_requested_at")
    @JsonIgnore
    private Instant deletionRequestedAt;

    public boolean isActiveAccount() {
        return "ACTIVE".equals(accountState);
    }

    public void requestDeletion(Instant now) {
        this.accountState = "DELETION_PENDING";
        this.deletionRequestedAt = now;
        this.email = "deleted-" + id + "@deleted.invalid";
        this.password = null;
        this.birthDate = null;
        this.emailVerifiedAt = null;
        this.username = "탈퇴한 사용자";
        this.socialId = null;
        this.socialType = SocialType.LOCAL;
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    public boolean isGoogleLinked() {
        return socialType == SocialType.GOOGLE && socialId != null && !socialId.isBlank();
    }

    public void linkGoogle(String subject) {
        this.socialType = SocialType.GOOGLE;
        this.socialId = subject;
    }

    public static User createLocalUser(String email, String password, String username, Role authority,
                                       String publicId) {
        return User.builder()
                .email(email)
                .password(password)
                .username(username)
                .socialType(SocialType.LOCAL)
                .authority(authority)
                .publicId(publicId)
                .build();
    }

    public static User createSocialUser(String email, String username, SocialType socialType, String socialId,
                                        Role authority, String publicId) {
        return User.builder()
                .email(email)
                .username(username)
                .socialType(socialType)
                .socialId(socialId)
                .authority(authority)
                .publicId(publicId)
                .build();
    }
}
