package jp.co.translacat.domain.support.repository;

import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.support.entity.SupportMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    @EntityGraph(attributePaths = {"sender", "recipient"})
    @Query("select m from SupportMessage m where :admin = true or m.recipient.id = :userId")
    Page<SupportMessage> search(
            @Param("admin") boolean admin, @Param("userId") Long userId, Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from SupportMessage m join fetch m.sender join fetch m.recipient where m.id = :id")
    Optional<SupportMessage> findLockedById(@Param("id") Long id);
}
