package jp.co.translacat.domain.support.repository;

import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.support.entity.SupportInquiry;
import jp.co.translacat.domain.support.enums.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, Long> {

    @EntityGraph(attributePaths = "user")
    @Query("""
            select i from SupportInquiry i
            where (:admin = true or i.user.id = :userId)
              and (:status is null or i.status = :status)
              and lower(i.title) like :search escape '!'
            """)
    Page<SupportInquiry> search(
            @Param("admin") boolean admin,
            @Param("userId") Long userId,
            @Param("status") InquiryStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SupportInquiry i join fetch i.user where i.id = :id")
    Optional<SupportInquiry> findLockedById(@Param("id") Long id);
}
