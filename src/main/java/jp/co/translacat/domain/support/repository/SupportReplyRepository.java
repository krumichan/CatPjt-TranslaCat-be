package jp.co.translacat.domain.support.repository;

import jp.co.translacat.domain.support.entity.SupportReply;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportReplyRepository extends JpaRepository<SupportReply, Long> {

    @EntityGraph(attributePaths = "author")
    List<SupportReply> findByInquiry_IdOrderByIdAsc(Long inquiryId);

    Optional<SupportReply> findByIdAndInquiry_Id(Long id, Long inquiryId);
}
