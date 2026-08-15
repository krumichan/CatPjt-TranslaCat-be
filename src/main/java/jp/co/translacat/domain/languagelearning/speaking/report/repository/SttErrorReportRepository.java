package jp.co.translacat.domain.languagelearning.speaking.report.repository;

import jp.co.translacat.domain.languagelearning.speaking.report.entity.SttErrorReport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SttErrorReportRepository
        extends JpaRepository<SttErrorReport, Long> {

    Optional<SttErrorReport> findByIdAndUserId(Long id, Long userId);
}
