package jp.co.translacat.domain.novel.novel.service;

import jp.co.translacat.domain.novel.author.entity.Author;
import jp.co.translacat.domain.novel.author.service.AuthorService;
import jp.co.translacat.domain.novel.genre.entity.Genre;
import jp.co.translacat.domain.novel.genre.service.GenreService;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.repository.NovelRepository;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NovelSafeSaver {
    private final NovelRepository novelRepository;

    private final AuthorService authorService;
    private final GenreService genreService;

    // 반드시 성공 시켜야 할 목록으로 별도 트랜잭션 처리.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Novel saveNovel(Platform platform, NovelContext ctx) {
        return this.saveNovels(platform, List.of(ctx)).getFirst();
    }

    // 반드시 성공 시켜야 할 목록으로 별도 트랜잭션 처리.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Novel> saveNovels(Platform platform, List<NovelContext> contextList) {
        return this.saveNovels(platform, null, contextList);
    }

    // 반드시 성공 시켜야 할 목록으로 별도 트랜잭션 처리.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Novel> saveNovels(Platform platform, String genreIdentifier, List<NovelContext> contextList) {
        List<Novel> processedList = new ArrayList<>();

        // 소설 존재 여부 확인.
        List<Novel> existingNovels =
                novelRepository.findAllByPlatformIdAndIdentifierIn(
                        platform.getId(), contextList.stream().map(NovelContext::getIdentifier).toList());
        Map<String, Novel> existingNovelMap = existingNovels.stream()
                .collect(Collectors.toMap(Novel::getIdentifier, r -> r, (oldValue, newValue) -> oldValue));

        // 작가 존재 여부 확인.
        List<Author> existingAuthors = authorService.findAuthors(
                platform.getId(), contextList.stream().map(NovelContext::getAuthorIdentifier).toList());
        Map<String, Author> existingAuthorMap = existingAuthors.stream()
                .collect(Collectors.toMap(Author::getIdentifier, r -> r, (oldValue, newValue) -> oldValue));

        // 장르 존재 여부 확인.
        Map<String, Genre> existingGenreMap = new HashMap<>();
        Genre literalGenre = null;
        if (Objects.nonNull(genreIdentifier)) {
            literalGenre = genreService.getGenre(genreIdentifier);
        }

        for (NovelContext ctx : contextList) {

            // 해당하는 작가가 있는지 조회하여 있으면 그대로 쓰고 아니면 생성해서 쓰기.
            Author author = existingAuthorMap.get(ctx.getAuthorIdentifier());
            if (Objects.isNull(author)) {
                author = authorService.create(
                        platform,
                        ctx.getAuthorIdentifier(),
                        ctx.getAuthor().getRawJa(),
                        ctx.getAuthor().getJa(),
                        ctx.getAuthor().getKo());

                // 중복 등록 방지.
                existingAuthorMap.put(author.getIdentifier(), author);
            }

            // 장르 존재하면 기존 장르 쓰고 없으면 DB에서 호출.
            if (Objects.isNull(literalGenre)) {
                literalGenre = existingGenreMap.get(ctx.getGenreText());
                if (Objects.isNull(literalGenre)) {
                    literalGenre = this.genreService.getGenre(platform.getId(), ctx.getGenreText());
                    existingGenreMap.put(ctx.getGenreText(), literalGenre);
                }
            }

            // 해당 소설이 있는지 조회 후 삽입/갱신 처리.
            Novel maybeNovel = existingNovelMap.get(ctx.getIdentifier());
            if (!Objects.isNull(maybeNovel)) {
                // [UPDATE] 이미 있다면 가져와서 정보 갱신
                maybeNovel.updateIfChanged(
                        literalGenre, author,
                        ctx.getTitle().getRawJa(), ctx.getTitle().getJa(), ctx.getTitle().getKo(),
                        ctx.getStatus().getRawJa(), ctx.getStatus().getJa(), ctx.getStatus().getKo(),
                        ctx.getSynopsis().getRawJa(), ctx.getSynopsis().getJa(), ctx.getSynopsis().getKo(),
                        ctx.isShortStory());

                processedList.add(maybeNovel);
            } else {
                // [INSERT] 없다면 새로 생성
                Novel newNovel = this.novelRepository.save(Novel.create(
                        platform, literalGenre, author, ctx.getIdentifier(),
                        ctx.getTitle().getRawJa(), ctx.getTitle().getJa(), ctx.getTitle().getKo(),
                        ctx.getStatus().getRawJa(), ctx.getStatus().getJa(), ctx.getStatus().getKo(),
                        ctx.getSynopsis().getRawJa(), ctx.getSynopsis().getJa(), ctx.getSynopsis().getKo(),
                        ctx.isShortStory()));
                processedList.add(newNovel);

                // 중복 등록 방지.
                existingNovelMap.put(newNovel.getIdentifier(), newNovel);
            }
        }

        return processedList;
    }
}
