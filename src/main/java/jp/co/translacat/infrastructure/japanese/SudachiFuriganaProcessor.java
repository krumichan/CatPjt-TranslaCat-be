package jp.co.translacat.infrastructure.japanese;

import com.worksap.nlp.sudachi.Config;
import com.worksap.nlp.sudachi.DictionaryFactory;
import com.worksap.nlp.sudachi.Morpheme;
import com.worksap.nlp.sudachi.Tokenizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SudachiFuriganaProcessor extends AbstractFuriganaProcessor {
    private Tokenizer tokenizer;

    @Value("${sudachi.dictionary.path}")
    private String dictPath;

    @PostConstruct
    public void init() throws IOException {

        File dictFile = new File(dictPath);
        if (!dictFile.exists()) {
            throw new IOException("사전 파일을 찾을 수 없습니다: " + dictFile.getAbsolutePath());
        }

        // Sudachi 설정: 주입받은 경로(dictFile)를 직접 사용
        Config sudachiConfig = Config.defaultConfig().systemDictionary(dictFile.toPath());
        this.tokenizer = new DictionaryFactory().create(sudachiConfig).create();
    }

    private SudachiFuriganaProcessor(String dictPath) throws IOException {
        this.dictPath = dictPath;
        this.init();
    }

    public static void main(String[] args) throws IOException {
        // 1. 프로세서 생성 및 사전 세팅
        final SudachiFuriganaProcessor processor = new SudachiFuriganaProcessor("src/main/resources/system_full.dic");

        // [수정] Sudachi가 흔히 분석하는 단어들로 사전 구성
        java.util.Map<String, String> testDict = new java.util.LinkedHashMap<>();

        // 1. 복합 한자어 (Sudachi가 하나로 묶어 뱉을 때 매칭 테스트)
        testDict.put("想定外", "そうていいがい");

        // 2. 오쿠리가나가 있는 동사 (가장 멀리 있는 매칭 포인트 테스트용)
        testDict.put("繰り返す", "くりかえす");

        // 3. 중복 방지 테스트용 (종선님의 요청대로 유지)
        testDict.put("猫猫", "マオマオ");

        // 4. 짧은 한자어 (기본 매칭 테스트)
        testDict.put("存在", "そんざい");

        testDict.put("いつの間にか", "いつのまにか");

        processor.syncCachedDict(testDict);

        System.out.println("=== [Sudachi 엔진 기반 테스트 시작] ===");

        // [테스트 1] 종선님이 주신 원문 3개
        System.out.println("\n1. 보스전 문장 (突き刺さった 사전 적용 여부):");
        String result1 = processor.convertToRuby("その脚は鎧を容易く貫き、ボスの胴体に突き刺さった。");
        System.out.println(result1);

        System.out.println("\n2. 격차 문장 (示している 분석 테스트):");
        String result2 = processor.convertToRuby("彼我の間にあまりにも大きすぎる差が存在していたことを示している。");
        System.out.println(result2);

        System.out.println("\n3. 보물전 문장 (太刀打ちできない 테스트):");
        String result3 = processor.convertToRuby("こんな宝物殿に本当にティノが太刀打ちできない程の幻影がいるのも想定外だし、いつの間にか救助対象が生きているのも想定外だ。");
        System.out.println(result3);

        // [테스트 2] 이번에 수정한 "루비 중복 방지" 테스트
        System.out.println("\n4. 중복 태그 방지 테스트 (이미 루비가 있는 경우):");
        // 원문에 이미 <ruby>태그가 있는 경우, 사전의 '猫猫'가 이중으로 달리면 안 됨
        String alreadyRuby = "<ruby>猫猫<rt>マオマオ</rt></ruby>は可愛い. 猫猫";
        String result4 = processor.convertToRuby(alreadyRuby);
        System.out.println(result4);

        // [테스트 3] 상세 루비 매칭 테스트
        System.out.println("\n5. 상세 루비 매칭 테스트:");
        String result5 = "繰り返す (사전): " + processor.convertToRuby("繰り返す");
        String result6 = "輝かしい (엔진): " + processor.convertToRuby("輝かしい");
        System.out.println(result5);
        System.out.println(result6);

        System.out.println("\n=== [테스트 종료] ===");
    }

    @Override
    protected String analyzeWithEngine(String segment) {
        if (segment == null || segment.isBlank()) return segment;

        // Mode.A를 사용하여 세밀하게 분절 (루비 생성에 최적)
        List<Morpheme> morphemes = tokenizer.tokenize(Tokenizer.SplitMode.A, segment);
        StringBuilder sb = new StringBuilder();

        for (Morpheme m : morphemes) {
            String surface = m.surface();
            String reading = m.readingForm();

            // 읽기 정보가 없거나 한자가 없으면 그대로 추가
            if (reading == null || reading.equals("*") || !containsKanji(surface)) {
                sb.append(surface);
                continue;
            }

            // 부모 클래스의 유틸리티 메서드들을 활용해 루비 생성
            String hiraganaReading = convertKatakanaToHiragana(reading);
            sb.append(makeFineGrainedRuby(surface, hiraganaReading));
        }
        return sb.toString();
    }
}
