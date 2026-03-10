package jp.co.translacat.infrastructure.scraping.syosetu.parser;

import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.novel.episode.model.EpisodeContentContext;
import jp.co.translacat.domain.novel.episode.model.EpisodePagerContext;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.ranking.novel.model.NovelRankingContext;
import jp.co.translacat.domain.novel.search.novel.model.NovelSearchContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.global.utils.PathUtil;
import jp.co.translacat.infrastructure.scraping.syosetu.enums.SyosetuNovelStatus;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SyosetuParser {

    private final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("p=(\\d+)");

    public NovelRankingContext parseNovelRanking(Element element) {
        return NovelRankingContext.builder()
                .pageNumberContext(this.extractRankingPageNumbers(element))
                .novelContexts(this.extractRankingItems(element))
                .build();
    }

    public NovelSearchContext parseNovelSearch(Element element) {
        return NovelSearchContext.builder()
            .pageNumberContext(this.extractSearchPageNumbers(element))
            .novelContexts(this.extractSearchItems(element))
            .build();
    }

    public NovelContext parseNovelDetail(Element element) {

        // 1. 제목 및 소설 식별자 추출
        Element titleLink = Objects.requireNonNull(element.selectFirst(".p-infotop-title a"));
        String novelIdentifier = PathUtil.extractPath(titleLink.attr("href")).replaceAll("/", "");

        // 2. 연재 상태 추출
        String statusText = Objects.requireNonNull(element.selectFirst(".p-infotop-type__type")).text();
        SyosetuNovelStatus status = SyosetuNovelStatus.of(statusText);

        // 3. 소설 상세 정보 추출.
        NovelDetailData data = this.extractNovelDetailData(element);

        return NovelContext.builder()
                .identifier(novelIdentifier)
                .authorIdentifier(data.authorIdentifier)
                .genreText(data.smallGenre)
                .title(TranslationUnit.of(titleLink.text()))
                .author(TranslationUnit.of(data.author))
                .synopsis(TranslationUnit.of(data.synopsis))
                .status(TranslationUnit.of(statusText, status.getRubyJa(), status.getKo()))
                .isShortStory(status.isShortStory())
                .build();
    }

    public List<RawEpisodeContext> extractEpisodes(Element element) {
        List<RawEpisodeContext> episodes = new ArrayList<>();

        Elements items = element.select(".p-eplist__sublist");
        for (Element item : items) {
            Element subtitleElement = item.select(".p-eplist__subtitle").first();

            String subtitle = Objects.requireNonNull(subtitleElement).text();
            String href = subtitleElement.attr("href");

            String identifier = this.extractEpisodeId(href);

            episodes.add(RawEpisodeContext.builder()
                    .sequence(Integer.parseInt(identifier))
                    .identifier(identifier)
                    .title(TranslationUnit.of(subtitle)).build());
        }

        return episodes;
    }

    public EpisodePagerContext parseEpisodePager(Element element) {
        Elements pagerCenterElements = element.select(".c-pager.c-pager--center");

        if (!pagerCenterElements.isEmpty()) {

            Element pagerCenterElement = pagerCenterElements.getFirst();
            Elements aElements = pagerCenterElement.select(".c-pager__item");

            // 이전 Identifier 추출.
            Element prevElement = aElements.select(".c-pager__item--before").first();
            String prevIdentifier = Objects.isNull(prevElement) ?
                    null : this.extractEpisodeId(prevElement.attr("href"));

            // 다음 Identifier 추출.
            Element nextElement = aElements.select(".c-pager__item--next").first();
            String nextIdentifier = Objects.isNull(nextElement) ?
                    null : this.extractEpisodeId(nextElement.attr("href"));

            // 목록 Identifier 추출.
            Element listElement = aElements.stream()
                    .filter(elem -> !elem.hasClass("c-pager__item--before") && !elem.hasClass("c-pager__item--next"))
                    .findFirst().orElse(null);
            String listIdentifier = null;
            if (!Objects.isNull(listElement)) {
                String listHref = listElement.attr("href");
                listIdentifier = PathUtil.extractPath(listHref).replaceAll("/", "");
            }

            return EpisodePagerContext.builder()
                    .prevIdentifier(prevIdentifier)
                    .listIdentifier(listIdentifier)
                    .nextIdentifier(nextIdentifier)
                    .build();
        }

        return EpisodePagerContext.empty();
    }

    public List<RawEpisodeContext> parseCurrentEpisodeInfo(Element element, String episodeId) {
        Element titleElement = element.selectFirst(".p-novel__title");
        String titleRawJa = Objects.requireNonNull(titleElement).text();
        return Collections.singletonList(
                RawEpisodeContext.builder()
                        .sequence(Integer.parseInt(episodeId))
                        .identifier(episodeId)
                        .title(TranslationUnit.of(titleRawJa))
                        .build()
        );
    }

    public List<EpisodeContentContext> parseEpisodeContents(Element element) {
        List<EpisodeContentContext> episodeContentContexts = new ArrayList<>();

        Elements lines = element.select(".js-novel-text p");

        for (Element line : lines) {

            // 1. id 속성값 가져오기 (예: "L1", "L5" 등)
            String idAttr = line.id();

            // id 속성이 없으면 생략.
            if (idAttr.isEmpty()) {
                continue;
            }

            // 2. id가 비어있지 않고 숫자를 포함하는지 확인 (방어 로직)
            int sequence;
            try {
                // "L1" -> "1" -> 1 로 변환
                if (idAttr.startsWith("Lp")) { // 서문
                    sequence = Integer.parseInt(idAttr.substring(2));
                } else if (idAttr.startsWith("La")) { // 후기
                    sequence = 90000 + Integer.parseInt(idAttr.substring(2));
                } else if (idAttr.startsWith("L")) { // 본문
                    sequence = 10000 + Integer.parseInt(idAttr.substring(1));
                } else {
                    continue; // 예상치 못한 형식은 스킵
                }
            } catch (NumberFormatException e) {
                continue; // 숫자가 아닌 경우 방어 로직
            }

            // 라인 복사
            Element lineCopy = line.clone();
            lineCopy.select("rt, rp").remove();
            String pureText = lineCopy.text().trim();
            String originalHtml = line.html().trim();

            episodeContentContexts.add(EpisodeContentContext.builder()
                    .sequence(sequence)
                    .content(TranslationUnit.of(pureText, originalHtml, ""))
                    .build());
        }

        return episodeContentContexts;
    }

    public String extractNovelDetailUrlFromHeader(Element element) {
        Elements detailElements = element.select(".c-menu__item.c-menu__item--headnav");
        if (detailElements.isEmpty()) {
            return null;
        }

        Element detailElement = detailElements.first();

        // 소설 작품 정보 URL 취득.
        return Objects.isNull(detailElement) ? "" : detailElement.attr("href");
    }

    public String extractEpisodeId(String href) {
        final Pattern EPISODE_ID_PATTERN = Pattern.compile("(\\d+)/?$");

        Matcher matcher = EPISODE_ID_PATTERN.matcher(href);

        if (matcher.find()) {
            // 1. 숫자가 발견된 경우 (예: /n1596lr/2/ -> "2")
            return matcher.group(1);
        } else {

            // 2. 숫자가 발견되지 않은 경우
            // 단편 소설 포맷(/n1596lr/)인지 한 번 더 검증하거나, 바로 "0" 세팅
            if (href.endsWith("/")) {
                return "0";
            }

            // 이외에 정말 이상한 형식인 경우에만 예외 발생
            throw new IllegalArgumentException(
                    String.format("invalid episode id: href-[%s]", href));
        }
    }

    public PageNumberContext parseEpisodeListPageNumbers(Element element) {
        Elements pages = element.select(".c-pager__item");
        if (!pages.isEmpty()) {
            return PageNumberContext.builder()
                    .first(this.extractPageNumber(Objects.requireNonNull(pages.select(".c-pager__item--first").first())))
                    .prev(this.extractPageNumber(Objects.requireNonNull(pages.select(".c-pager__item--before").first())))
                    .next(this.extractPageNumber(Objects.requireNonNull(pages.select(".c-pager__item--next").first())))
                    .last(this.extractPageNumber(Objects.requireNonNull(pages.select(".c-pager__item--last").first())))
                    .build();
        }

        return PageNumberContext.empty();
    }

    public Integer extractPageNumber(Element element) {

        if (Objects.isNull(element)) {
            return null;
        }

        // href 추출.
        String href = element.attr("href");

        // 없으면 null.
        if (href.isEmpty()) return null;

        // Page 정규화로 번호 추출.
        Matcher matcher = PAGE_NUMBER_PATTERN.matcher(href);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return null;
    }

    private PageNumberContext extractRankingPageNumbers(Element element) {

        // 페이지 번호 습득.
        Elements pages = element.select(".c-pager__item");

        if (!pages.isEmpty()) {

            Element prev = pages.stream()
                    .filter(e -> e.attr("title").contains("前の50作品へ")).findFirst().orElse(null);

            Element next = pages.stream()
                    .filter(e -> e.attr("title").contains("次の50作品へ")).findFirst().orElse(null);

            return PageNumberContext.builder()
                    .first(this.extractPageNumber(Objects.requireNonNull(prev)))
                    .prev(this.extractPageNumber(Objects.requireNonNull(prev)))
                    .next(this.extractPageNumber(Objects.requireNonNull(next)))
                    .last(this.extractPageNumber(Objects.requireNonNull(next)))
                    .build();
        }

        return PageNumberContext.empty();
    }

    private List<NovelContext> extractRankingItems(Element element) {
        List<NovelContext> rankings = new ArrayList<>();

        Elements items = element.select(".c-card.p-ranklist-item");
        for (Element item : items) {
            String rank = item.select(".c-rank-place__num").text();

            // 소설 제목 추출.
            Element titleElement = item.select(".p-ranklist-item__title a").first();
            String title = Objects.requireNonNull(titleElement).text();
            String titleHref = titleElement.attr("href");
            String titleIdentify = PathUtil.extractPath(titleHref).replaceAll("/", "");

            // 소설 작가 추출.
            Element authorElement = item.select(".p-ranklist-item__author a").first();
            String author = Objects.requireNonNull(authorElement).text();
            String authorHref = authorElement.attr("href");
            String authorIdentify = PathUtil.extractPath(authorHref).replaceAll("/", "");

            // 소설 상태 추출.
            String statusText = Objects.requireNonNull(item.select(".p-ranklist-item__infomation .p-ranklist-item__separator").first()).text();
            statusText = statusText.contains("(") ? statusText.substring(0, statusText.indexOf("(")) : statusText;
            SyosetuNovelStatus status = SyosetuNovelStatus.of(statusText.trim());

            // 소설 줄거리 추출.
            String synopsis = item.select(".p-ranklist-item__synopsis").text();

            rankings.add(NovelContext.builder()
                    .rank(Integer.parseInt(rank))
                    .identifier(titleIdentify)
                    .authorIdentifier(authorIdentify)
                    .isShortStory(status.isShortStory())
                    .title(TranslationUnit.of(title))
                    .author(TranslationUnit.of(author))
                    .status(TranslationUnit.of(status.getJa(), status.getRubyJa(), status.getKo()))
                    .synopsis(TranslationUnit.of(synopsis)).build());
        }

        return rankings;
    }

    private PageNumberContext extractSearchPageNumbers(Element element) {
        Element pagerDiv = element.selectFirst(".pager");
        if (Objects.isNull(pagerDiv)) {
            return PageNumberContext.empty();
        }

        // 1. 현재 페이지 추출.
        Element currentElement = pagerDiv.selectFirst("b");
        Integer current = null;
        if (Objects.nonNull(currentElement)) {
            current = Integer.parseInt(currentElement.text().replaceAll("[^0-9]", ""));
        }

        // 2. BACK(이전) 추출.
        Element prevElement = pagerDiv.selectFirst("a.backlink");
        Integer prev = null;
        if (Objects.nonNull(prevElement)) {
            String href = prevElement.attr("href");
            prev = Integer.parseInt(Objects.requireNonNull(PathUtil.getQueryParamFirst(href, "p")));
        }

        // 3. NEXT(다음) 추출.
        Element nextElement = pagerDiv.selectFirst("a.nextlink");
        Integer next = null;
        if (Objects.nonNull(nextElement)) {
            String href = nextElement.attr("href");
            next = Integer.parseInt(Objects.requireNonNull(PathUtil.getQueryParamFirst(href, "p")));
        }

        // 모든 번호 추출.
        List<Integer> pages = pagerDiv.select("a").stream()
            .filter(a -> !a.hasClass(".backlink") && !a.hasClass(".nextlink"))
            .map(a -> a.attr("href"))
            .map(href -> {
                String p = PathUtil.getQueryParamFirst(href, "p");
                return Objects.nonNull(p) ? Integer.parseInt(p) : null;
            })
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (Objects.nonNull(current) && !pages.contains(current)) {
            pages.add(current);
            Collections.sort(pages);
        }

        return PageNumberContext.builder()
            .current(current)
            .prev(prev)
            .next(next)
            .pages(pages)
            .build();
    }

    private List<NovelContext> extractSearchItems(Element element) {
        Elements searchedNovelDivs = element.getElementsByClass("searchkekka_box");

        List<NovelContext> novelContexts = new ArrayList<>();
        for (Element searchedNovelDiv : searchedNovelDivs) {
            // 1. 소설 제목 및 ID 추출.
            Element titleDiv = searchedNovelDiv.selectFirst(".novel_h");
            Element titleATage = Objects.requireNonNull(titleDiv).selectFirst("a");
            String title = Objects.requireNonNull(titleATage).text();
            String titleHref = titleATage.attr("href");
            String novelIdentifier = PathUtil.extractPath(titleHref).replaceAll("/", "");

            // 2. 소설 줄거리 추출.
            String synopsis = Objects.requireNonNull(searchedNovelDiv.selectFirst(".ex")).text();

            // 3. 작가명 및 작가 ID 추출.
            Element authorATag = searchedNovelDiv.selectFirst("a[href*='mypage.syosetu.com']");
            String author = Objects.requireNonNull(authorATag).text();
            String authorHref = authorATag.attr("href");
            String authorIdentifier = PathUtil.extractPath(authorHref).replaceAll("/", "");

            // 4. 연재 상태 추출.
            Element statusTd = searchedNovelDiv.selectFirst("td.left");
            String statusText = Objects.requireNonNull(statusTd).ownText().trim();
            statusText = statusText.contains("(") ? statusText.substring(0, statusText.indexOf("(")) : statusText;
            SyosetuNovelStatus status = SyosetuNovelStatus.of(statusText.trim());

            // 5. 장르명 추출.
            Element genreATag = searchedNovelDiv.selectFirst("a[href*='genre=']");
            String genreText = "";
            if (Objects.nonNull(genreATag)) {
                String mainGenre = genreATag.text();
                String subGenre = (genreATag.nextSibling() instanceof TextNode)
                    ? ((TextNode) Objects.requireNonNull(genreATag.nextSibling())).getWholeText().trim()
                    : "";
                genreText = mainGenre + subGenre;
            }

            novelContexts.add(NovelContext.builder()
                .identifier(novelIdentifier)
                .authorIdentifier(authorIdentifier)
                .genreText(genreText)
                .isShortStory(status.isShortStory())
                .title(TranslationUnit.of(title))
                .author(TranslationUnit.of(author))
                .status(TranslationUnit.of(status.getJa(), status.getRubyJa(), status.getKo()))
                .synopsis(TranslationUnit.of(synopsis)).build());
        }

        return novelContexts;
    }

    private record NovelDetailData(String synopsis, String author, String authorIdentifier, String smallGenre) {}
    private NovelDetailData extractNovelDetailData(Element element) {
        Elements dataList = element.select(".p-infotop-data dt");

        String synopsis = "";
        String author = "";
        String authorIdentifier = "";
        String smallGenre = "";

        for (Element dt : dataList) {
            String columnName = dt.text().trim();
            Element dd = dt.nextElementSibling(); // dt 바로 다음에 오는 dd 요소를 선택

            if (dd == null) continue;

            switch (columnName) {
                case "あらすじ":
                    synopsis = dd.text().trim();
                    break;

                case "作者名":
                    Element authorLink = dd.selectFirst("a");
                    if (authorLink != null) {
                        author = authorLink.text().trim();
                        String href = authorLink.attr("href");
                        // PathUtil을 사용하여 ID 추출
                        authorIdentifier = PathUtil.extractPath(href).replace("/", "");
                    } else {
                        author = dd.text().trim();
                    }
                    break;

                case "ジャンル":
                    smallGenre = dd.text().trim();
                    break;

                default:
                    // 다른 항목(N코드, 掲載日 등)은 무시하거나 필요시 추가
                    break;
            }
        }
        return new NovelDetailData(synopsis, author, authorIdentifier, smallGenre);
    }
}
