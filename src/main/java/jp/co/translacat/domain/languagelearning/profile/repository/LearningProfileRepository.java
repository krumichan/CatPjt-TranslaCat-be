package jp.co.translacat.domain.languagelearning.profile.repository;

import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningProfileRepository extends JpaRepository<LearningProfile, Long> {
    Optional<LearningProfile> findByUserId(Long userId);
}
