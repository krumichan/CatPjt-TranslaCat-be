package jp.co.translacat.domain.chat.friend.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Getter
@Table(name = "chat_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatProfile extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(length = 100, nullable = false)
    private String nickname;

    @Column
    private String comment;

    @Column
    private String iconPath;

    @Column
    private String backgroundPath;

    @Builder(access = AccessLevel.PRIVATE)
    private ChatProfile(User user, String nickname, String comment, String iconPath, String backgroundPath) {
        this.user = user;
        this.nickname = nickname;
        this.comment = comment;
        this.iconPath = iconPath;
        this.backgroundPath = backgroundPath;
    }

    public static ChatProfile create(User user, String nickname, String comment, String iconPath, String backgroundPath) {
        return ChatProfile.builder()
            .user(user)
            .nickname(nickname)
            .comment(comment)
            .iconPath(iconPath)
            .backgroundPath(backgroundPath)
            .build();
    }

    public boolean update(String nickname, String comment, String iconPath, String backgroundPath) {
        if (!Objects.equals(this.nickname, nickname) ||
            !Objects.equals(this.comment, comment) ||
            !Objects.equals(this.iconPath ,iconPath) ||
            !Objects.equals(this.backgroundPath ,backgroundPath)) {
            this.nickname = nickname;
            this.comment = comment;
            this.iconPath = iconPath;
            this.backgroundPath = backgroundPath;

            return true;
        }

        return false;
    }
}
