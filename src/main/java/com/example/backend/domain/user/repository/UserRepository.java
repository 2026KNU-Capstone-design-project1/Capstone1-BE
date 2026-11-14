
package com.example.backend.domain.user.repository;

import com.example.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    @Query(value = """
    SELECT name from users
        where login_Id=:loginId
    """, nativeQuery = true)
    String findNameByLoginId(@Param("loginId") String loginId);
}
