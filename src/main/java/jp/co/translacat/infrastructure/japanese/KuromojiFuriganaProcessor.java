package jp.co.translacat.infrastructure.japanese;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class KuromojiFuriganaProcessor extends AbstractFuriganaProcessor {

    private final Tokenizer tokenizer;

    public KuromojiFuriganaProcessor() {
        this.tokenizer = new Tokenizer();
    }

    @Override
    protected String analyzeWithEngine(String segment) {
        if (segment == null || segment.isBlank()) {
            return segment;
        }

        List<Token> tokens = tokenizer.tokenize(segment);
        StringBuilder result = new StringBuilder();

        for (Token token : tokens) {
            String surface = token.getSurface();
            String katakana = token.getReading();

            // 1. 읽기 정보가 없거나 한자가 포함되지 않은 경우 그대로 추가
            if (katakana == null || katakana.equals("*") || !containsKanji(surface)) {
                result.append(surface);
                continue;
            }

            // 2. 카타카나를 히라가나로 변환 및 루비 태그 생성
            String hiragana = convertKatakanaToHiragana(katakana);
            result.append(makeFineGrainedRuby(surface, hiragana));
        }

        return result.toString();
    }
}
