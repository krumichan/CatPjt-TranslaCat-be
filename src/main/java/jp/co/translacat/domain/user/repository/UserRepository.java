package jp.co.translacat.domain.user.repository;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);
    Optional<User> findBySocialIdAndSocialType(String socialId, SocialType socialType);
}
