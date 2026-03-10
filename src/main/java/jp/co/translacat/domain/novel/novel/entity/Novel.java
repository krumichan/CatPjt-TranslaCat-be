package jp.co.translacat.domain.novel.novel.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.novel.author.entity.Author;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.domain.novel.genre.entity.Genre;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Table(name = "novel", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform_id", "identifier"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Novel extends BaseAuditable {

    @Builder(access = AccessLevel.PRIVATE)
    private Novel(Platform platform, Genre genre, Author author, String identifier,
                  String title, String titleJa, String titleKo,
                  String status, String statusJa, String statusKo,
                  String synopsis, String synopsisJa, String synopsisKo,
                  boolean isShortStory) {
        this.platform = platform;
        this.genre = genre;
        this.author = author;
        this.identifier = identifier;
        this.title = title;
        this.titleJa = titleJa;
        this.titleKo = titleKo;
        this.status = status;
        this.statusJa = statusJa;
        this.statusKo = statusKo;
        this.synopsis = synopsis;
        this.synopsisJa = synopsisJa;
        this.synopsisKo = synopsisKo;
        this.isShortStory = isShortStory;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    @Column(length = 50, nullable = false)
    private String identifier;

    @Column(length = 2000)
    private String title;

    @Column(length = 2000)
    private String titleJa;

    @Column(length = 2000)
    private String titleKo;

    @Column
    private String status;

    @Column
    private String statusJa;

    @Column
    private String statusKo;

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Column(columnDefinition = "TEXT")
    private String synopsisJa;

    @Column(columnDefinition = "TEXT")
    private String synopsisKo;

    @Column
    private Boolean isShortStory;

    @OneToMany(mappedBy = "novel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NovelGenre> novelGenres = new ArrayList<>();

    private void addGenre(Genre genre) {
        NovelGenre novelGenre = NovelGenre.create(this, genre);
        this.novelGenres.add(novelGenre);
    }

    public static Novel create(Platform platform, Genre genre, Author author, String identifier,
                               String title, String titleJa, String titleKo,
                               String status, String statusJa, String statusKo,
                               String synopsis, String synopsisJa, String synopsisKo,
                               boolean isShortStory) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier는 필수입니다.");
        }

        Novel newNovel = Novel.builder()
                .platform(platform)
                .genre(genre)
                .author(author)
                .identifier(identifier)
                .title(title)
                .titleJa(titleJa)
                .titleKo(titleKo)
                .status(status)
                .statusJa(statusJa)
                .statusKo(statusKo)
                .synopsis(synopsis)
                .synopsisJa(synopsisJa)
                .synopsisKo(synopsisKo)
                .isShortStory(isShortStory)
                .build();

        newNovel.addGenre(genre);

        return newNovel;
    }

    public void update(Genre genre, Author author,
                       String title, String titleJa, String titleKo,
                       String status, String statusJa, String statusKo,
                       String synopsis, String synopsisJa, String synopsisKo,
                       boolean isShortStory) {
        this.genre = genre;
        this.author = author;
        this.title = title;
        this.titleJa = titleJa;
        this.titleKo = titleKo;
        this.status = status;
        this.statusJa = statusJa;
        this.statusKo = statusKo;
        this.synopsis = synopsis;
        this.synopsisJa = synopsisJa;
        this.synopsisKo = synopsisKo;
        this.isShortStory = isShortStory;
    }

    public boolean updateIfChanged(Genre genre, Author author,
                                   String title, String titleJa, String titleKo,
                                   String status, String statusJa, String statusKo,
                                   String synopsis, String synopsisJa, String synopsisKo,
                                   boolean isShortStory) {

        // 🌟 하나라도 변경되었는지 체크
        boolean changed = !Objects.equals(this.genre, genre) ||
                !Objects.equals(this.author, author) ||
                !Objects.equals(this.title, title) ||
                !Objects.equals(this.titleJa, titleJa) ||
                !Objects.equals(this.titleKo, titleKo) ||
                !Objects.equals(this.status, status) ||
                !Objects.equals(this.statusJa, statusJa) ||
                !Objects.equals(this.statusKo, statusKo) ||
                !Objects.equals(this.synopsis, synopsis) ||
                !Objects.equals(this.synopsisJa, synopsisJa) ||
                !Objects.equals(this.synopsisKo, synopsisKo) ||
                this.isShortStory != isShortStory;

        if (changed) {
            this.update(genre, author,
                title, titleJa, titleKo,
                status, statusJa, statusKo,
                synopsis, synopsisJa, synopsisKo,
                isShortStory);
        }

        return changed;
    }
}
