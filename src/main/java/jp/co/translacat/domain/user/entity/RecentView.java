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
                       String novelId, String episodeId, String title, String titleJa, String titleKo,
                       String titleEn, String targetLanguage) {
        this.user = user;
        this.platform = platform;
        this.recentViewType = recentViewType;
        this.novelId = novelId;
        this.episodeId = episodeId;
        this.title = title;
        this.titleJa = titleJa;
        this.titleKo = titleKo;
        this.titleEn = titleEn;
        this.targetLanguage = targetLanguage;
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

    @Column(length = 2000)
    private String titleEn;

    @Column(nullable = false, length = 2)
    private String targetLanguage = "ko";

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime viewedAt;

    public static RecentView create(User user, Platform platform, RecentViewType recentViewType,
                                    String novelId, String episodeId, String title, String titleJa, String titleKo) {
        return create(user, platform, recentViewType, novelId, episodeId, title, titleJa, titleKo, null, "ko");
    }

    public static RecentView create(User user, Platform platform, RecentViewType recentViewType,
                                    String novelId, String episodeId, String title, String titleJa, String titleKo,
                                    String titleEn, String targetLanguage) {
        String target = jp.co.translacat.novel.domain.NovelTargetLanguage.normalize(targetLanguage);
        // 새 영어 방문은 한국어 제목으로 위장하지 않는다. 번역이 없으면 원제와 함께 보존한다.
        return RecentView.builder()
                .user(user)
                .platform(platform)
                .recentViewType(recentViewType)
                .novelId(novelId)
                .episodeId(episodeId)
                .title(title)
                .titleJa(titleJa)
                .titleKo("ko".equals(target) ? titleKo : null)
                .titleEn("en".equals(target) && hasTitle(titleEn) ? titleEn : null)
                .targetLanguage(target)
                .build();
    }

    public void update(String title, String titleJa, String titleKo) {
        update(title, titleJa, titleKo, null, "ko");
    }

    public void update(String title, String titleJa, String titleKo, String titleEn, String targetLanguage) {
        String target = jp.co.translacat.novel.domain.NovelTargetLanguage.normalize(targetLanguage);
        // 같은 책의 기존 최근 기록을 갱신하되 다른 언어의 제목은 유지한다.
        // 원제 정보가 없는 내부 reader 호출도 이미 저장한 원문을 지우지 않는다.
        if (hasTitle(title)) this.title = title;
        if (hasTitle(titleJa)) this.titleJa = titleJa;
        if ("en".equals(target)) {
            if (hasTitle(titleEn)) this.titleEn = titleEn;
        } else if (hasTitle(titleKo)) {
            // 원문만 가진 한국어 reader 재진입도 이미 알려진 번역 제목을 지우지 않는다.
            this.titleKo = titleKo;
        }
        this.targetLanguage = target;
        this.viewedAt = LocalDateTime.now();
    }

    public String getTargetLanguage() {
        return targetLanguage == null ? "ko" : targetLanguage;
    }

    private static boolean hasTitle(String value) {
        return value != null && !value.isBlank();
    }
}
