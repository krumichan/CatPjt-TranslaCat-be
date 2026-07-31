package jp.co.translacat.domain.chat.openchat.support;

import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Component
public class OpenChatMemberCodeGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final OpenChatMemberProfileRepository profileRepository;
    private final RandomGenerator randomGenerator;

    @Autowired
    public OpenChatMemberCodeGenerator(
            OpenChatMemberProfileRepository profileRepository
    ) {
        this(profileRepository, new SecureRandom());
    }

    OpenChatMemberCodeGenerator(
            OpenChatMemberProfileRepository profileRepository,
            RandomGenerator randomGenerator
    ) {
        this.profileRepository = profileRepository;
        this.randomGenerator = randomGenerator;
    }

    public String generate() {
        for (int attempt = 0;
             attempt < OpenChatPolicy.MEMBER_CODE_MAX_ATTEMPTS;
             attempt++) {
            String candidate = createCandidate();
            if (!profileRepository.existsByMemberCode(candidate)) {
                return candidate;
            }
        }

        throw new BusinessException(
                "OPEN 채팅 멤버 코드를 생성할 수 없습니다.",
                OpenChatErrorCode.MEMBER_CODE_GENERATION_FAILED
        );
    }

    private String createCandidate() {
        StringBuilder builder = new StringBuilder("OC-");
        for (int i = 0;
             i < OpenChatPolicy.MEMBER_CODE_RANDOM_LENGTH;
             i++) {
            builder.append(ALPHABET[
                    randomGenerator.nextInt(ALPHABET.length)
            ]);
        }
        return builder.toString();
    }
}
