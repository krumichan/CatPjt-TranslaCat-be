package jp.co.translacat.domain.user.profile.repository;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserAndDeletedFalse(User user);

    Optional<UserProfile> findByUserIdAndDeletedFalse(Long userId);

    boolean existsByUserAndDeletedFalse(User user);
}