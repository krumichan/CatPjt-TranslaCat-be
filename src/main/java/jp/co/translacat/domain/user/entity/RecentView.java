package jp.co.translacat.domain.user.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.user.enums.RecentViewType;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "recent_view", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "platform_id", "recent_view_type", "novel_id", "episode_id"})
})
@NoArgsConstructor
public class RecentView {

    @Builder(access = AccessLevel.PROTECTED)
    private RecentView(User user, Platform platform, RecentViewType recentViewType,
                       String novelId, String episodeId, String title, String titleJa, String titleKo) {
        this.user = user;
        this.platform = platform;
        this.recentViewType = recentViewType;
        this.novelId = novelId;
        this.episodeId = episodeId;
        this.title = title;
        this.titleJa = titleJa;
        this.titleKo = titleKo;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecentViewType recentViewType;

    @Column(nullable = false)
    private String novelId;

    @Column
    private String episodeId;

    @Column(length = 2000)
    private String title;

    @Column(length = 2000)
    private String titleJa;

    @Column(length = 2000)
    private String titleKo;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime viewedAt;

    public static RecentView create(User user, Platform platform, RecentViewType recentViewType,
                                    String novelId, String episodeId, String title, String titleJa, String titleKo) {
        return RecentView.builder()
            .user(user)
            .platform(platform)
            .recentViewType(recentViewType)
            .novelId(novelId)
            .episodeId(episodeId)
            .title(title)
            .titleJa(titleJa)
            .titleKo(titleKo)
            .build();
    }

    public void update(String title, String titleJa, String titleKo) {
        this.title = title;
        this.titleJa = titleJa;
        this.titleKo = titleKo;
        this.viewedAt = LocalDateTime.now();
    }
}
