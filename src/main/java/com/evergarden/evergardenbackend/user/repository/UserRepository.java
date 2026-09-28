package com.evergarden.evergardenbackend.user.repository;

import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    /** 대시보드의 {@code totalUserCount}(ADMIN-03) — 탈퇴한 회원은 뺀다. */
    long countByStatusNot(UserStatus status);

    /** 대시보드의 {@code newUserCountToday}(ADMIN-03). */
    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    /** 대시보드의 {@code sanctionedUserCount}(ADMIN-03) — 경고 또는 차단 상태. */
    long countByStatusIn(Collection<UserStatus> statuses);
}
