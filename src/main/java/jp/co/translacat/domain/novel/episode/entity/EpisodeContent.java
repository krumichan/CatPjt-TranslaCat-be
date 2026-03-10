package jp.co.translacat.domain.novel.episode.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.global.utils.ValidationUtil;
import lombok.*;

@Entity
@Getter
@Table(
    name = "episode_content",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"episode_id", "sequence"})
    },
    indexes = {
        @Index(columnList = "episode_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EpisodeContent extends BaseAuditable {

    @Builder(access = AccessLevel.PRIVATE)
    private EpisodeContent(Episode episode, int sequence, String content, String contentJa, String contentKo) {
        this.episode = episode;
        this.sequence = sequence;
        this.content = content;
        this.contentJa = contentJa;
        this.contentKo = contentKo;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Column
    private int sequence;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String contentJa;

    @Column(columnDefinition = "TEXT")
    private String contentKo;

    public static EpisodeContent create(Episode episode, int sequence, String content, String contentJa, String contentKo) {
        return EpisodeContent.builder()
            .episode(episode)
            .sequence(sequence)
            .content(content)
            .contentJa(contentJa)
            .contentKo(contentKo)
            .build();
    }

    public void updateContentJa(String contentJa) {
        this.contentJa = contentJa;
    }

    public boolean isTranslationRequired() {
        // 1. 원문이 비어있으면 번역할 필요 없음
        if (this.content == null || this.content.trim().replaceAll("[\\s\\u3000]", "").isEmpty()) {
            return false;
        }

        // 2. 한국어 번역본이 없으면 번역 필요
        if (!this.getContent().trim().isEmpty() && (this.getContentKo() == null || this.getContentKo().trim().isEmpty())) {
            return true;
        }

        // 3. default 후리가나가 없는 경우 다시 생성.
        if (this.getContentJa() != null && this.getContentJa().contains("()")) {
            return true;
        }

        // 4. 한국어 번역 결과물에 일본어가 10% 이상 섞여있는 경우 다시 생성.
        if (ValidationUtil.calculateJapaneseRatio(this.getContentKo()) >= 0.1) {
            return true;
        }

        return false;
    }
}
