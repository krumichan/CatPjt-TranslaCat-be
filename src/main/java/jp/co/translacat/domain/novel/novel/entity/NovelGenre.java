package jp.co.translacat.domain.novel.novel.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.domain.novel.genre.entity.Genre;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "genre_novel")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelGenre extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id", nullable = false)
    private Novel novel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    public static NovelGenre create(Novel novel, Genre genre) {
        NovelGenre novelGenre = new NovelGenre();
        novelGenre.novel = novel;
        novelGenre.genre = genre;
        return novelGenre;
    }
}
