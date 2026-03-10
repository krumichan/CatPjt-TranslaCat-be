package jp.co.translacat.domain.user.repository;

import jp.co.translacat.domain.user.entity.UserAllowed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAllowedRepository extends JpaRepository<UserAllowed, Long> {
    Optional<UserAllowed> findByEmail(String email);
}
