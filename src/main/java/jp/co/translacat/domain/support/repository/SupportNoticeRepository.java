package jp.co.translacat.domain.support.repository;

import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.support.entity.SupportNotice;
import jp.co.translacat.domain.support.enums.NoticeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface SupportNoticeRepository extends JpaRepository<SupportNotice, Long> {

    @Override
    @EntityGraph(attributePaths = "author")
    Page<SupportNotice> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("""
            select n from SupportNotice n
            where n.noticeType = :noticeType
              and not exists (
                  select d.id from SupportNoticeDismissal d
                  where d.notice.id = n.id and d.user.id = :userId
              )
            order by n.id desc
            """)
    List<SupportNotice> findUndismissed(
            @Param("userId") Long userId, @Param("noticeType") NoticeType noticeType
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from SupportNotice n where n.id = :noticeId")
    int deleteByNoticeId(@Param("noticeId") Long noticeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from SupportNotice n join fetch n.author where n.id = :id")
    Optional<SupportNotice> findLockedById(@Param("id") Long id);
}
