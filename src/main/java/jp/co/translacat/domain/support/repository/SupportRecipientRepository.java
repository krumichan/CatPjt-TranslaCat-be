package jp.co.translacat.domain.support.repository;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupportRecipientRepository extends Repository<User, Long> {

    @Query("""
            select u from User u
            where lower(u.username) like :query escape '!'
               or lower(u.email) like :query escape '!'
               or lower(u.publicId) like :query escape '!'
            order by u.id
            """)
    List<User> search(@Param("query") String query, Pageable pageable);

    List<User> findByAuthority(Role authority);
}
