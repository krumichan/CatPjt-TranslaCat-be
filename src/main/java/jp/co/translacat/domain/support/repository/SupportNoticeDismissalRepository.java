package jp.co.translacat.domain.support.repository;

import jp.co.translacat.domain.support.entity.SupportNoticeDismissal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SupportNoticeDismissalRepository extends JpaRepository<SupportNoticeDismissal, Long> {

    boolean existsByUser_IdAndNotice_Id(Long userId, Long noticeId);

    @Modifying
    @Query("delete from SupportNoticeDismissal d where d.notice.id = :noticeId")
    int deleteByNoticeId(@Param("noticeId") Long noticeId);
}
