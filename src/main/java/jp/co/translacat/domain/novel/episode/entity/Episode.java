package jp.co.translacat.domain.novel.episode.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Getter
@Table(name = "episode", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"novel_id", "identifier"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Episode extends BaseAuditable {

    @Builder(access = AccessLevel.PRIVATE)
    private Episode(Novel novel, String identifier, String title, String titleJa, String titleKo) {
        this.novel = novel;
        this.identifier = identifier;
        this.title = title;
        this.titleJa = titleJa;
        this.titleKo = titleKo;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id", nullable = false)
    private Novel novel;

    @Column(length = 50, nullable = false)
    private String identifier;

    @Column(length = 2000)
    private String title;

    @Column(length = 2000)
    private String titleJa;

    @Column(length = 2000)
    private String titleKo;

    public static Episode create(Novel novel, String identifier, String title, String titleJa, String titleKo) {
        if (Objects.isNull(identifier) || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier는 필수입니다.");
        }

        return Episode.builder()
                .novel(novel)
                .identifier(identifier)
                .title(title)
                .titleJa(titleJa)
                .titleKo(titleKo)
                .build();
    }

    public boolean updateIfChanged(Novel novel, String title, String titleJa, String titleKo) {

        // 하나라도 변경되었는지 체크
        boolean changed = !Objects.equals(this.novel, novel) ||
                !Objects.equals(this.title, title) ||
                !Objects.equals(this.titleJa, titleJa) ||
                !Objects.equals(this.titleKo, titleKo);

        if (changed) {
            this.update(novel, title, titleJa, titleKo);
        }

        return changed;
    }

    public void update(Novel novel, String title, String titleJa, String titleKo) {
        this.novel = novel;
        this.title = title;
        this.titleJa = titleJa;
        this.titleKo = titleKo;
    }
}
