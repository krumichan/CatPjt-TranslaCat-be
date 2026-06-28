package jp.co.translacat.domain.user.block.repository;

import jp.co.translacat.domain.user.block.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long>, UserBlockRepositoryCustom {

    Optional<UserBlock> findByIdAndDeletedFalse(Long id);
}
