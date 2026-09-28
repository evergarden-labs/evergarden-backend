package com.evergarden.evergardenbackend.user.repository;

import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    /** 대시보드의 {@code totalUserCount}(ADMIN-03) — 탈퇴한 회원은 뺀다. */
    long countByStatusNot(UserStatus status);

    /** 대시보드의 {@code newUserCountToday}(ADMIN-03). */
    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    /** 대시보드의 {@code sanctionedUserCount}(ADMIN-03) — 경고 또는 차단 상태. */
    long countByStatusIn(Collection<UserStatus> statuses);

    /** 닉네임 부분 일치·상태로 걸러 최신 가입순으로(ADMIN-12). 둘 다 없으면 전체. */
    @Query("""
            SELECT u FROM User u
            WHERE (:keyword IS NULL OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR u.status = :status)
            ORDER BY u.createdAt DESC
            """)
    Page<User> search(@Param("keyword") String keyword, @Param("status") UserStatus status, Pageable pageable);
}
