package jp.co.translacat.domain.chat.friend.repository;

import jp.co.translacat.domain.chat.friend.entity.ChatProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FriendRepository extends JpaRepository<ChatProfile, Long>, FriendRepositoryCustom {
    boolean existsByUserId(Long userId);
    Optional<ChatProfile> findByUserId(Long userId);
}
