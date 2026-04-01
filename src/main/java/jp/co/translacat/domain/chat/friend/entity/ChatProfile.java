package jp.co.translacat.domain.chat.friend.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.Getter;

@Entity
@Getter
@Table(name = "chat_profile")
public class ChatProfile extends BaseAuditable {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 100, nullable = false)
    private String nickname;

    @Column
    private String comment;

    @Column
    private String iconPath;

    @Column
    private String backgroundPath;
}
