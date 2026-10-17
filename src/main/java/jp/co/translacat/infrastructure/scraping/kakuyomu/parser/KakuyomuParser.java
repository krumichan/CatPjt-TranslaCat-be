package jp.co.translacat.infrastructure.scraping.kakuyomu.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.novel.episode.model.EpisodeContentContext;
import jp.co.translacat.domain.novel.episode.model.EpisodeDetailContext;
import jp.co.translacat.domain.novel.episode.model.EpisodePagerContext;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.ranking.novel.model.NovelRankingContext;
import jp.co.translacat.domain.novel.search.novel.model.NovelSearchContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.infrastructure.scraping.kakuyomu.KakuyomuUrls;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** 공개 HTML과 그 HTML에 포함된 초기 상태만 읽으며 별도 API·인증 페이지는 호출하지 않는다. */
@Component
public class KakuyomuParser {
    private static final Safelist RUBY_TAGS = Safelist.none().addTags("ruby", "rb", "rt", "rp", "br", "em");
    private static final Map<String, String> GENRES = Map.ofEntries(
            Map.entry("FANTASY", "異世界ファンタジー"), Map.entry("ACTION", "現代ファンタジー"),
            Map.entry("SF", "SF"), Map.entry("LOVE_STORY", "恋愛"), Map.entry("ROMANCE", "ラブコメ"),
            Map.entry("DRAMA", "現代ドラマ"), Map.entry("HORROR", "ホラー"), Map.entry("MYSTERY", "ミステリー"),
            Map.entry("NONFICTION", "エッセイ・ノンフィクション"), Map.entry("HISTORY", "歴史・時代・伝奇"),
            Map.entry("CRITICISM", "創作論・評論"), Map.entry("OTHERS", "詩・童話・その他"));
    private final ObjectMapper json;

    public KakuyomuParser() {
        this(new ObjectMapper());
    }

    @Autowired
    public KakuyomuParser(ObjectMapper json) {
        this.json = json;
    }

    public NovelRankingContext parseNovelRanking(Element document) {
        JsonNode state = state(document);
        JsonNode connection = connection(state, "rankedWorks(");
        return NovelRankingContext.builder().pageNumberContext(pages(document, connection, false))
                .novelContexts(catalog(document, state, connection, true)).build();
    }

    public NovelSearchContext parseNovelSearch(Element document) {
        JsonNode state = state(document);
        JsonNode connection = connection(state, "searchWorks(");
        return NovelSearchContext.builder().pageNumberContext(pages(document, connection, true))
                .novelContexts(catalog(document, state, connection, false)).build();
    }

    public NovelDetailContext parseNovelDetail(Element document, String workId) {
        KakuyomuUrls.workUrl(workId);
        requireCanonical(document, workId, null);
        JsonNode state = state(document);
        JsonNode work = state.path("Work:" + workId);
        NovelContext novel = work.isObject() ? novel(work, state, 0) : detailFallback(document, workId);

        // 전체 공개 목차를 원래 순서로 읽는다. 예약 회차와 유료 전용 타입은 대상에 넣지 않는다.
        Map<String, RawEpisodeContext> episodes = new LinkedHashMap<>();
        for (JsonNode chapterRef : work.path("tableOfContentsV2")) {
            JsonNode chapter = dereference(state, chapterRef);
            for (JsonNode episodeRef : chapter.path("episodeUnions")) {
                JsonNode episode = dereference(state, episodeRef);
                if (!"Episode".equals(episode.path("__typename").asText())) continue;
                addEpisode(episodes, episode.path("id").asText(), episode.path("title").asText());
            }
        }
        if (episodes.isEmpty()) {
            for (Element link : document.select("a[class*=WorkTocSection_link], .widget-toc-episode a[href]")) {
                String id = episodeLink(link.attr("href"), workId).orElse(null);
                if (id == null) continue;
                Element title = link.selectFirst("[class*=WorkTocSection_title], .widget-toc-episode-titleLabel");
                addEpisode(episodes, id, plain(title == null ? link : title).trim());
            }
        }
        return NovelDetailContext.builder().novelContext(novel).pageNumberContext(PageNumberContext.empty())
                .rawEpisodeContexts(List.copyOf(episodes.values())).build();
    }

    public EpisodeDetailContext parseEpisode(Element document, String workId, String episodeId) {
        KakuyomuUrls.episodeUrl(workId, episodeId);
        requireCanonical(document, workId, episodeId);
        Element title = episodeTitle(document);
        List<EpisodeContentContext> contents = new ArrayList<>();
        for (Element paragraph : bodyParagraphs(document)) {
            contents.add(EpisodeContentContext.builder().sequence(contents.size() + 1)
                    .content(TranslationUnit.of(plain(paragraph), safeMarkup(paragraph), "")).build());
        }
        String workTitle = text(document.selectFirst("#contentMain-header-workTitle, #workTitle"));
        String author = text(document.selectFirst("#contentMain-header-author"));
        NovelContext novel = NovelContext.builder().identifier(workId).authorIdentifier("").genreText("")
                .title(TranslationUnit.of(workTitle)).author(TranslationUnit.of(author))
                .synopsis(TranslationUnit.of("")).status(TranslationUnit.of("連載中", "", "연재 중")).build();
        return EpisodeDetailContext.builder().episodeContentContexts(contents)
                .episodePagerContext(pager(document, workId))
                .novelDetailContext(NovelDetailContext.builder().novelContext(novel).pageNumberContext(PageNumberContext.empty())
                        .rawEpisodeContexts(List.of(RawEpisodeContext.builder().identifier(episodeId).sequence(1)
                                .title(TranslationUnit.of(plain(title).trim())).build())).build()).build();
    }

    public SourceEpisode parseSourceEpisode(Element document, EpisodeKey key, SentenceSegmenter segmenter) {
        if (!"kakuyomu".equals(key.platform())) throw new NovelProblem("PLATFORM_NOT_SUPPORTED", 422);
        requireCanonical(document, key.novel(), key.episode());
        Element title = episodeTitle(document);
        List<String> paragraphs = new ArrayList<>();
        List<List<SourceEpisode.RubyToken>> readings = new ArrayList<>();

        // rt/rp는 원문에서 분리하며 UTF-16 offset과 빈 문단·개행은 그대로 분절기에 전달한다.
        for (Element paragraph : bodyParagraphs(document)) {
            StringBuilder source = new StringBuilder();
            List<SourceEpisode.RubyToken> ruby = new ArrayList<>();
            paragraph.childNodes().forEach(node -> append(node, source, ruby));
            paragraphs.add(source.toString());
            readings.add(ruby);
        }
        EpisodePagerContext navigation = pager(document, key.novel());
        return segmenter.segment(key, plain(title).trim(), paragraphs,
                navigation.getPrevIdentifier(), navigation.getNextIdentifier(), readings);
    }

    public String validatedNextPage(Element document, String currentUrl) {
        for (Element link : document.select("link[rel=next], a[rel=next], [class*=Pager_next] a, a[href*=page=]")) {
            Optional<String> next = KakuyomuUrls.pagination(currentUrl, link.attr("href"));
            if (next.isPresent()) return next.get();
        }
        return null;
    }

    private List<NovelContext> catalog(Element document, JsonNode state, JsonNode connection, boolean ranking) {
        List<NovelContext> novels = new ArrayList<>();
        Map<String, Integer> ranks = ranks(document);
        int offset = (page(document) - 1) * 100;
        if (connection.path("nodes").isArray()) {
            if (connection.path("nodes").size() > 1000) throw new NovelProblem("SOURCE_SIZE_INVALID", 413);
            for (JsonNode ref : connection.path("nodes")) {
                JsonNode work = dereference(state, ref);
                if (!"Work".equals(work.path("__typename").asText())) throw unsupported();
                int rank = ranking ? ranks.getOrDefault(work.path("id").asText(), offset + novels.size() + 1) : 0;
                novels.add(novel(work, state, rank));
            }
            return List.copyOf(novels);
        }

        // 예전 공개 HTML도 작품 제목의 실제 링크를 기준으로만 찾는다. 추천/리뷰 링크를 제목으로 섞지 않는다.
        Map<String, NovelContext> unique = new LinkedHashMap<>();
        for (Element heading : document.select("h3, .widget-workCard-title")) {
            Element title = heading.selectFirst("a[href*=/works/]");
            if (title == null) continue;
            String id = workLink(title.attr("href")).orElse(null);
            if (id == null || title.attr("href").contains("/episodes/")) continue;
            Element card = card(heading);
            Element author = card.selectFirst("a[href^=/users/]");
            Element synopsis = card.selectFirst(".widget-workCard-introduction, [class*=TruncateMultipleLines], a.LinkAppearance_block__UJrud");
            String status = card.text().contains("完結済") ? "完結済" : "連載中";
            unique.putIfAbsent(id, NovelContext.builder().identifier(id).authorIdentifier(authorId(author))
                    .title(unit(title)).author(unit(author)).synopsis(unit(synopsis)).status(status(status))
                    .genreText(text(card.selectFirst("a[href^=/genres/]"))).isShortStory(false)
                    .rank(ranking ? ranks.getOrDefault(id, offset + unique.size() + 1) : 0).build());
        }
        if (unique.isEmpty() && !document.text().contains("見つかりません") && !document.text().contains("検索結果")) throw unsupported();
        return List.copyOf(unique.values());
    }

    private NovelContext novel(JsonNode work, JsonNode state, int rank) {
        String id = work.path("id").asText();
        KakuyomuUrls.workUrl(id);
        String title = work.path("title").asText();
        if (title.isBlank()) throw unsupported();
        JsonNode author = dereference(state, work.path("author"));
        String authorName = work.path("alternateAuthorName").asText("");
        if (authorName.isBlank()) authorName = author.path("activityName").asText(author.path("name").asText());
        String serial = "COMPLETED".equals(work.path("serialStatus").asText()) ? "完結済" : "連載中";
        boolean shortStory = "完結済".equals(serial) && (work.has("totalCharacterCount")
                ? work.path("totalCharacterCount").asLong(Long.MAX_VALUE) < 20000 : work.path("publicEpisodeCount").asInt(2) == 1);
        return NovelContext.builder().identifier(id).authorIdentifier(author.path("name").asText())
                .title(TranslationUnit.of(title)).author(TranslationUnit.of(authorName))
                .synopsis(TranslationUnit.of(work.path("introduction").asText("")))
                .status(status(serial)).genreText(GENRES.getOrDefault(work.path("genre").asText(), ""))
                .isShortStory(shortStory).rank(rank).build();
    }

    private NovelContext detailFallback(Element document, String workId) {
        Element title = document.selectFirst("#workTitle, h1 a[href='/works/" + workId + "'], h1");
        if (title == null || text(title).isBlank()) throw unsupported();
        Element author = document.selectFirst("#workAuthor-activityName a, [class*=WorkAuthorBox] a[href^=/users/]");
        Element introduction = document.selectFirst("#introduction, [class*=CollapseTextWithKakuyomuLinks_collapseText]");
        return NovelContext.builder().identifier(workId).title(unit(title)).author(unit(author))
                .authorIdentifier(authorId(author)).synopsis(unit(introduction)).status(status("連載中"))
                .genreText(text(document.selectFirst("a[href^=/genres/]"))).isShortStory(false).build();
    }

    private void addEpisode(Map<String, RawEpisodeContext> episodes, String id, String title) {
        KakuyomuUrls.episodeUrl("1", id);
        if (title.isBlank()) throw unsupported();
        episodes.putIfAbsent(id, RawEpisodeContext.builder().identifier(id).sequence(episodes.size() + 1)
                .title(TranslationUnit.of(title)).build());
        if (episodes.size() > 20000) throw new NovelProblem("SOURCE_SIZE_INVALID", 413);
    }

    private JsonNode state(Element document) {
        Element script = document.selectFirst("script#__NEXT_DATA__");
        if (script == null) return MissingNode.getInstance();
        if (script.data().length() > 4_000_000) throw new NovelProblem("SOURCE_TOO_LARGE", 413);
        try {
            return json.readTree(script.data()).path("props").path("pageProps").path("__APOLLO_STATE__");
        } catch (Exception invalid) {
            throw unsupported();
        }
    }

    private JsonNode connection(JsonNode state, String prefix) {
        var fields = state.path("ROOT_QUERY").fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (field.getKey().startsWith(prefix)) return field.getValue();
        }
        return MissingNode.getInstance();
    }

    private JsonNode dereference(JsonNode state, JsonNode reference) {
        return reference.has("__ref") ? state.path(reference.path("__ref").asText()) : reference;
    }

    private Map<String, Integer> ranks(Element document) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (Element item : document.select("[class*=Rankings_item], .widget-workCard")) {
            Element number = item.selectFirst("[class*=RankingWorkBox_rank], .widget-workCard-rankLabel");
            Element link = item.selectFirst("h3 a[href*=/works/], .widget-workCard-title a[href]");
            if (number == null || link == null || !number.text().trim().matches("[0-9]{1,7}")) continue;
            workLink(link.attr("href")).ifPresent(id -> ranks.put(id, Integer.parseInt(number.text().trim())));
        }
        return ranks;
    }

    private Element card(Element heading) {
        Element current = heading;
        for (int depth = 0; depth < 7 && current.parent() != null; depth++, current = current.parent()) {
            if (current.selectFirst("[class*=WorkMeta], .widget-workCard-introduction") != null) return current;
        }
        return heading.parent() == null ? heading : heading.parent();
    }

    private PageNumberContext pages(Element document, JsonNode connection, boolean search) {
        String currentUrl = origin(document);
        int current = page(document);
        TreeSet<Integer> numbers = new TreeSet<>();
        numbers.add(current);
        for (Element link : document.select("a[href*=page=]")) {
            KakuyomuUrls.samePage(currentUrl, link.attr("href")).ifPresent(url -> numbers.add(KakuyomuUrls.pageNumber(url)));
        }
        String next = validatedNextPage(document, currentUrl);
        Integer last = search && connection.has("totalCount") ? Math.max(1, (connection.path("totalCount").asInt() + 19) / 20) : null;
        return PageNumberContext.builder().current(current).pages(List.copyOf(numbers))
                .first(current > 1 ? 1 : null).prev(current > 1 ? current - 1 : null)
                .next(next == null ? null : current + 1).last(last).build();
    }

    private int page(Element document) {
        return KakuyomuUrls.pageNumber(origin(document));
    }

    private String origin(Element document) {
        try {
            return KakuyomuUrls.requirePublic(document.baseUri()).toString();
        } catch (IllegalArgumentException ignored) {
            Element canonical = document.selectFirst("link[rel=canonical]");
            return canonical == null ? "https://kakuyomu.jp/search" : KakuyomuUrls.requirePublic(canonical.attr("href")).toString();
        }
    }

    private void requireCanonical(Element document, String workId, String episodeId) {
        Element canonical = document.selectFirst("link[rel=canonical]");
        if (canonical == null) return;
        try {
            String value = canonical.attr("href");
            if (!KakuyomuUrls.workId(value).equals(workId)
                    || episodeId != null && !KakuyomuUrls.episodeId(value, workId).equals(episodeId)) throw unsupported();
        } catch (IllegalArgumentException malformed) {
            throw unsupported();
        }
    }

    private Element episodeTitle(Element document) {
        Element title = document.selectFirst(".widget-episodeTitle, #episodeTitle");
        if (title == null || plain(title).isBlank()) throw unsupported();
        return title;
    }

    private List<Element> bodyParagraphs(Element document) {
        Element body = document.selectFirst(".widget-episodeBody, #episodeBody");
        if (body == null || body.select("p").isEmpty()) throw unsupported();
        return List.copyOf(body.select("p"));
    }

    private EpisodePagerContext pager(Element document, String workId) {
        Element prev = document.selectFirst("link[rel=prev], #contentMain-readPreviousEpisode, #contentMain-readPrevEpisode");
        Element next = document.selectFirst("link[rel=next], #contentMain-readNextEpisode");
        return EpisodePagerContext.builder().listIdentifier(workId)
                .prevIdentifier(prev == null ? null : episodeLink(prev.attr("href"), workId).orElse(null))
                .nextIdentifier(next == null ? null : episodeLink(next.attr("href"), workId).orElse(null)).build();
    }

    private Optional<String> episodeLink(String href, String workId) {
        try {
            return Optional.of(KakuyomuUrls.episodeId(URI.create(KakuyomuUrls.workUrl(workId)).resolve(href).toString(), workId));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private Optional<String> workLink(String href) {
        try {
            return Optional.of(KakuyomuUrls.workId(URI.create("https://kakuyomu.jp").resolve(href).toString()));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private String authorId(Element author) {
        if (author == null) return "";
        String href = author.attr("href");
        return href.matches("/users/[^/?#]{1,100}/?") ? href.substring(7).replaceAll("/$", "") : "";
    }

    private TranslationUnit status(String serial) {
        return TranslationUnit.of(serial, "", "完結済".equals(serial) ? "완결" : "연재 중");
    }

    private TranslationUnit unit(Element element) {
        return element == null ? TranslationUnit.of("") : TranslationUnit.of(plain(element).trim(), safeMarkup(element), "");
    }

    private String safeMarkup(Element element) {
        Element safe = element.clone();
        safe.select("script,style,iframe,object").remove();
        return Jsoup.clean(safe.html(), RUBY_TAGS);
    }

    private String text(Element element) {
        return element == null ? "" : plain(element).trim();
    }

    private String plain(Element element) {
        StringBuilder value = new StringBuilder();
        element.childNodes().forEach(node -> append(node, value, new ArrayList<>()));
        return value.toString();
    }

    private void append(Node node, StringBuilder text, List<SourceEpisode.RubyToken> ruby) {
        if (node instanceof TextNode value) {
            text.append(value.getWholeText());
            return;
        }
        if (!(node instanceof Element element) || List.of("rt", "rp", "script", "style", "iframe", "object").contains(element.tagName())) return;
        if ("br".equals(element.tagName())) {
            text.append('\n');
            return;
        }
        if ("ruby".equals(element.tagName())) {
            int start = text.length();
            element.childNodes().forEach(child -> append(child, text, new ArrayList<>()));
            String reading = element.select("rt").stream().map(Element::text).collect(java.util.stream.Collectors.joining());
            if (text.length() > start && !reading.isBlank()) {
                ruby.add(new SourceEpisode.RubyToken(start, text.length(), text.substring(start), reading));
            }
            return;
        }
        element.childNodes().forEach(child -> append(child, text, ruby));
    }

    private NovelProblem unsupported() {
        return new NovelProblem("SOURCE_PAGE_NOT_SUPPORTED", 422);
    }
}
