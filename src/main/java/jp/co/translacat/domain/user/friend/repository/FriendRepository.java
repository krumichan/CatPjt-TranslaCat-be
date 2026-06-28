package jp.co.translacat.domain.user.friend.repository;

import jp.co.translacat.domain.user.friend.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FriendRepository extends JpaRepository<Friend, Long>, FriendRepositoryCustom {

    Optional<Friend> findByIdAndDeletedFalse(Long id);
}
