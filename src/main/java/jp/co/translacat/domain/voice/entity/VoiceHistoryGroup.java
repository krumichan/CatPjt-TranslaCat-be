package jp.co.translacat.domain.voice.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "voice_history_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceHistoryGroup extends BaseAuditable {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column
    private String title;

    @Builder(access = AccessLevel.PRIVATE)
    private VoiceHistoryGroup(String id, String title, User user) {
        this.id = id;
        this.title = title;
        this.user = user;
        this.createdBy = user.getEmail();
    }

    public static VoiceHistoryGroup create(String id, User user) {
        String defaultTitle = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 기록";
        return VoiceHistoryGroup.builder()
            .id(id)
            .user(user)
            .title(defaultTitle)
            .build();
    }

    public boolean update(String title) {
        if (title == null || title.isBlank() || title.equals(this.title)) {
            return false;
        }

        this.title = title;
        return true;
    }
}
