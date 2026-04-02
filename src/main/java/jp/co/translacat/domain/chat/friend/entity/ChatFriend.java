package jp.co.translacat.domain.chat.friend.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.chat.friend.enums.FriendStatus;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.Getter;

@Entity
@Getter
@Table(name = "chat_friend", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_profile_id", "friend_profile_id"})
})
public class ChatFriend extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private ChatProfile userProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_profile_id", nullable = false)
    private ChatProfile friendProfile;

    @Enumerated(EnumType.STRING)
    private FriendStatus status;
}
