package jp.co.translacat.domain.chat.openchat.profile.repository;

import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OpenChatMemberProfileRepository
        extends JpaRepository<OpenChatMemberProfile, Long> {

    boolean existsByMemberCode(String memberCode);

    Optional<OpenChatMemberProfile> findByChatRoomMemberId(
            Long chatRoomMemberId
    );
}
