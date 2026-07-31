package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;
import jp.co.translacat.domain.chat.language.service.UserChatLanguageSettingService;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatOwnerProfileCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatRoomCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.openchat.support.OpenChatMemberCodeGenerator;
import jp.co.translacat.domain.chat.openchat.support.OpenChatPolicy;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OpenChatRoomCommandService {

    private final ChatRoomRepository chatRoomRepository;
    private final OpenChatRoomRepository openChatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final OpenChatMemberProfileRepository profileRepository;
    private final UserService userService;
    private final UserChatLanguageSettingService languageSettingService;
    private final OpenChatMemberCodeGenerator memberCodeGenerator;
    private final OpenChatRoomQueryService queryService;

    public OpenChatRoomDetailResponseDto create(
            Long loginUserId,
            OpenChatRoomCreateRequestDto request
    ) {
        ValidatedCreateRequest validated = validate(request);
        User owner = userService.getById(loginUserId);

        ChatRoom chatRoom = ChatRoom.createOpenRoom(
                validated.name(),
                validated.description(),
                owner
        );
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        OpenChatRoom openChatRoom = OpenChatRoom.create(
                savedChatRoom,
                validated.visibility(),
                validated.maxMemberCount()
        );
        openChatRoomRepository.save(openChatRoom);

        ChatLanguageSettingResult languageSetting =
                resolveLanguageSetting(loginUserId);
        ChatRoomMember ownerMember =
                ChatRoomMember.createOwner(
                        savedChatRoom,
                        owner,
                        languageSetting.originalLanguageCode(),
                        languageSetting.translationLanguageCode(),
                        languageSetting.showOriginal(),
                        languageSetting.showTranslation()
                );
        ChatRoomMember savedOwnerMember =
                chatRoomMemberRepository.save(ownerMember);

        OpenChatMemberProfile ownerProfile =
                OpenChatMemberProfile.create(
                        savedOwnerMember,
                        memberCodeGenerator.generate(),
                        validated.nickname(),
                        validated.profileImageObjectKey()
                );
        profileRepository.save(ownerProfile);

        chatRoomRepository.flush();

        return queryService.getDetail(
                loginUserId,
                savedChatRoom.getId()
        );
    }

    private ValidatedCreateRequest validate(
            OpenChatRoomCreateRequestDto request
    ) {
        if (request == null) {
            throw new BusinessException(
                    "OPEN 채팅방 생성 요청은 필수입니다.",
                    OpenChatErrorCode.REQUEST_REQUIRED
            );
        }

        String name = normalizeRequired(
                request.name(),
                100,
                "OPEN 채팅방 이름은 필수입니다.",
                OpenChatErrorCode.NAME_REQUIRED,
                "OPEN 채팅방 이름은 100자 이하여야 합니다.",
                OpenChatErrorCode.NAME_TOO_LONG
        );
        String description = normalizeRequired(
                request.description(),
                500,
                "OPEN 채팅방 설명은 필수입니다.",
                OpenChatErrorCode.DESCRIPTION_REQUIRED,
                "OPEN 채팅방 설명은 500자 이하여야 합니다.",
                OpenChatErrorCode.DESCRIPTION_TOO_LONG
        );

        if (request.visibility() == null) {
            throw new BusinessException(
                    "OPEN 채팅방 공개 범위는 필수입니다.",
                    OpenChatErrorCode.VISIBILITY_REQUIRED
            );
        }

        int maxMemberCount = request.maxMemberCount() == null
                ? OpenChatPolicy.DEFAULT_MAX_MEMBER_COUNT
                : request.maxMemberCount();
        if (maxMemberCount < OpenChatPolicy.MIN_MAX_MEMBER_COUNT
                || maxMemberCount
                > OpenChatPolicy.MAX_MEMBER_COUNT_LIMIT) {
            throw new BusinessException(
                    "OPEN 채팅방 최대 인원은 2명 이상 100명 이하여야 합니다.",
                    OpenChatErrorCode.MAX_MEMBER_COUNT_INVALID
            );
        }

        OpenChatOwnerProfileCreateRequestDto ownerProfile =
                request.ownerProfile();
        if (ownerProfile == null) {
            throw new BusinessException(
                    "OWNER OPEN 프로필은 필수입니다.",
                    OpenChatErrorCode.OWNER_PROFILE_REQUIRED
            );
        }

        String nickname = normalizeRequired(
                ownerProfile.nickname(),
                50,
                "OPEN 채팅 닉네임은 필수입니다.",
                OpenChatErrorCode.NICKNAME_REQUIRED,
                "OPEN 채팅 닉네임은 50자 이하여야 합니다.",
                OpenChatErrorCode.NICKNAME_TOO_LONG
        );
        String objectKey = normalizeObjectKey(
                ownerProfile.profileImageObjectKey()
        );

        return new ValidatedCreateRequest(
                name,
                description,
                request.visibility(),
                maxMemberCount,
                nickname,
                objectKey
        );
    }

    private String normalizeRequired(
            String value,
            int maxLength,
            String requiredMessage,
            String requiredCode,
            String lengthMessage,
            String lengthCode
    ) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    requiredMessage,
                    requiredCode
            );
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(
                    lengthMessage,
                    lengthCode
            );
        }
        return normalized;
    }

    private String normalizeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        String normalized = objectKey.trim();
        if (normalized.length() > 500
                || !normalized.startsWith(
                        OpenChatPolicy
                                .PROFILE_IMAGE_OBJECT_KEY_PREFIX
                )) {
            throw new BusinessException(
                    "OPEN 채팅 프로필 이미지 Object Key가 유효하지 않습니다.",
                    OpenChatErrorCode
                            .PROFILE_IMAGE_OBJECT_KEY_INVALID
            );
        }
        return normalized;
    }

    private ChatLanguageSettingResult resolveLanguageSetting(
            Long userId
    ) {
        ChatLanguageSettingResult result =
                languageSettingService.resolveDefault(userId);
        if (result != null) {
            return result;
        }
        return new ChatLanguageSettingResult(
                "ko",
                "ja",
                true,
                true,
                false,
                ChatLanguageSettingSource.SYSTEM
        );
    }

    private record ValidatedCreateRequest(
            String name,
            String description,
            OpenChatVisibility visibility,
            int maxMemberCount,
            String nickname,
            String profileImageObjectKey
    ) {
    }
}
