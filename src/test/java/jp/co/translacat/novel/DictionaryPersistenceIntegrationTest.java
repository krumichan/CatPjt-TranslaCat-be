package jp.co.translacat.novel;

import jakarta.persistence.EntityManager;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.author.entity.Author;
import jp.co.translacat.domain.novel.dictionary.dto.DictionaryRegisterDto;
import jp.co.translacat.domain.novel.dictionary.repository.JapaneseDictionaryRepository;
import jp.co.translacat.domain.novel.dictionary.service.DictionaryService;
import jp.co.translacat.domain.novel.episode.entity.Episode;
import jp.co.translacat.domain.novel.episode.entity.EpisodeContent;
import jp.co.translacat.domain.novel.genre.entity.Genre;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.global.config.QueryDslConfig;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.infrastructure.japanese.KuromojiFuriganaProcessor;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:novel-dictionary-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({QueryDslConfig.class, DictionaryService.class, FuriganaProcessor.class, KuromojiFuriganaProcessor.class,
        SudachiFuriganaProcessor.class, NovelRubyFormatter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DictionaryPersistenceIntegrationTest {
    @Autowired private EntityManager entities;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JapaneseDictionaryRepository dictionaries;
    @Autowired private DictionaryService service;
    @Autowired private NovelRubyFormatter ruby;

    @Test
    void registrationAndCorrectionSurviveCommitWithoutChangingExistingEpisodeContent() {
        // 준비: 격리 DB에 이미 번역된 회차를 보존하고 Sudachi의 기본 발음을 확인한다.
        long contentId = new TransactionTemplate(transactionManager).execute(status -> savedEpisodeContent());
        List<String> original = storedContent(contentId);
        assertThat(ruby.tokens("猫").getFirst().reading()).isEqualTo("ねこ");

        // 실행: 신규 표면형을 저장한 다음 동일 표면형의 발음을 교정한다.
        assertThat(service.register(request("猫", "まお"))).isTrue();
        assertThat(dictionaries.findBySurface("猫").orElseThrow().getReading()).isEqualTo("まお");
        assertThat(ruby.tokens("猫").getFirst().reading()).isEqualTo("まお");
        assertThat(ruby.markup("猫")).isEqualTo("<ruby>猫<rt>まお</rt></ruby>");

        assertThat(service.register(request("猫", "みゃお"))).isTrue();

        // 검증: DB에 중복 항목이 없고 새 reader/목록 발음만 바뀌며 원문과 기존 번역은 그대로다.
        assertThat(dictionaries.count()).isEqualTo(1);
        assertThat(dictionaries.findBySurface("猫").orElseThrow().getReading()).isEqualTo("みゃお");
        assertThat(ruby.tokens("猫").getFirst().reading()).isEqualTo("みゃお");
        assertThat(ruby.markup("猫")).isEqualTo("<ruby>猫<rt>みゃお</rt></ruby>");
        assertThat(storedContent(contentId)).isEqualTo(original);
    }

    private long savedEpisodeContent() {
        Platform platform = new Platform();
        ReflectionTestUtils.setField(platform, "code", PlatformCode.SYOSETU);
        entities.persist(platform);

        Genre genre = new Genre();
        ReflectionTestUtils.setField(genre, "platform", platform);
        ReflectionTestUtils.setField(genre, "identifier", "fantasy");
        entities.persist(genre);

        Author author = Author.create(platform, "author", "作者", "作者", "작가");
        entities.persist(author);
        Novel novel = Novel.create(platform, genre, author, "n-test", "猫の旅", "猫の旅", "고양이의 여행",
                "連載", "連載", "연재", "猫が歩く。", "猫が歩く。", "고양이가 걷는다.", false);
        entities.persist(novel);

        Episode episode = Episode.create(novel, "1", "第一話", "第一話", "제1화");
        entities.persist(episode);
        EpisodeContent content = EpisodeContent.create(episode, 1, "猫が歩く。",
                "<ruby>猫<rt>ねこ</rt></ruby>が歩く。", "고양이가 걷는다.");
        entities.persist(content);
        entities.flush();
        return content.getId();
    }

    private List<String> storedContent(long id) {
        return jdbc.queryForObject("SELECT content, content_ja, content_ko FROM episode_content WHERE id = ?",
                (row, index) -> List.of(row.getString(1), row.getString(2), row.getString(3)), id);
    }

    private DictionaryRegisterDto request(String surface, String reading) {
        DictionaryRegisterDto value = new DictionaryRegisterDto();
        ReflectionTestUtils.setField(value, "surface", surface);
        ReflectionTestUtils.setField(value, "reading", reading);
        return value;
    }
}
