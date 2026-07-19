package jp.co.translacat.domain.chat.message.service;

import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 채팅 메시지 응답에 표시할 발신자의 현재 프로필 이미지를 조회한다.
 *
 * 메시지 이력에는 이미지 URL/Object Key를 저장하지 않는다.
 * 응답을 생성하는 시점에 UserProfile의 현재 Object Key로 공개 URL을 만든다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageSenderProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileImageUrlResolver imageUrlResolver;

    public String resolveLatestProfileImageUrl(Long userId) {
        if (userId == null) {
            return null;
        }

        return userProfileRepository
                .findByUserIdAndDeletedFalse(userId)
                .map(imageUrlResolver::resolveProfileImageUrl)
                .orElse(null);
    }

    /**
     * 메시지 페이지에 포함된 발신자들을 일괄 조회하여 N+1을 방지한다.
     * 이미지가 없는 사용자는 Map에 포함하지 않으며 호출부에서 null로 처리한다.
     */
    public Map<Long, String> resolveLatestProfileImageUrlMap(
            Collection<Long> userIds
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> normalizedUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (normalizedUserIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> result = new HashMap<>();

        for (UserProfile userProfile :
                userProfileRepository.findByUserIdInAndDeletedFalse(
                        normalizedUserIds
                )) {
            String profileImageUrl =
                    imageUrlResolver.resolveProfileImageUrl(userProfile);

            if (profileImageUrl == null || profileImageUrl.isBlank()) {
                continue;
            }

            result.put(
                    userProfile.getUser().getId(),
                    profileImageUrl
            );
        }

        return Map.copyOf(result);
    }
}
