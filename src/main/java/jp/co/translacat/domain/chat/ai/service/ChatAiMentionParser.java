package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ChatAiMentionParser {

    /**
     * 한국어에서는 멘션 직후 조사를 붙여 쓰는 경우가 자연스럽다.
     * 예: {@code @Mika는}, {@code @Mi도}.
     *
     * 모든 한글 문자를 경계로 허용하면 {@code @Mikaela} 같은 부분 닉네임까지
     * 오인할 수 있으므로, 자주 사용하는 조사만 명시적으로 허용한다.
     */
    private static final String KOREAN_MENTION_PARTICLE_PATTERN =
            "(?:은|는|이|가|을|를|도|만|와|과|랑|이랑|의|에|에서|에게|한테|께|께서|으로|로|부터|까지|처럼|보다|하고)";

    public List<ChatRoomAiMember> findMentionedMembers(
            String content,
            List<ChatRoomAiMember> aiMembers
    ) {
        if (content == null || content.isBlank()
                || aiMembers == null || aiMembers.isEmpty()) {
            return List.of();
        }

        return aiMembers.stream()
                .filter(member -> member != null
                        && member.getAiAgent() != null
                        && containsMention(
                        content,
                        member.getAiAgent().getNickname()
                ))
                .toList();
    }

    public int countMentionTargets(
            String content,
            List<ChatRoomAiMember> aiMembers
    ) {
        return findMentionedMembers(content, aiMembers).size();
    }

    private boolean containsMention(
            String content,
            String nickname
    ) {
        if (nickname == null || nickname.isBlank()) {
            return false;
        }

        String expression = "(?iu)(?<![\\p{L}\\p{N}_])@"
                + Pattern.quote(nickname.trim())
                + "(?:"
                + "(?=$|[\\s\\p{P}\\p{S}])"
                + "|(?=" + KOREAN_MENTION_PARTICLE_PATTERN
                + "(?=$|[\\s\\p{P}\\p{S}]))"
                + ")";
        return Pattern.compile(expression)
                .matcher(content)
                .find();
    }
}
