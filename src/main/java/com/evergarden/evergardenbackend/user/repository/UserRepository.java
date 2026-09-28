package com.evergarden.evergardenbackend.user.repository;

import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 유효 신고 누적을 원자적으로 1 늘린다(ADMIN-10). 같은 회원을 겨냥한 서로 다른
     * 신고가 동시에 유효 판정되면, 엔티티를 불러와 고치고 저장하는 방식은 나중 트랜잭션이
     * 먼저 것의 증가분을 덮어써 잃어버린다(실전에서 확인 — 3건을 동시에 판정했더니
     * 최종값이 1이었다) — {@code UserGardenObjectRepository.tryGrow()}와 같은 이유로
     * DB에서 직접 한 문장으로 늘린다.
     */
    @Modifying
    @Query("UPDATE User u SET u.validReportCount = u.validReportCount + 1 WHERE u.id = :userId")
    void increaseValidReportCount(@Param("userId") Long userId);

    /**
     * 방금 늘린 누적값을 원시값으로 다시 읽는다(ADMIN-10). 같은 트랜잭션에서 이미
     * 로드한 {@code User}는 Hibernate 1차 캐시가 낡은 인스턴스를 그대로 돌려주므로,
     * 엔티티가 아닌 원시값 프로젝션으로 우회해야 방금 늘어난 진짜 값을 본다
     * ({@code UserGardenObjectRepository.findSnapshot()}와 같은 이유).
     */
    @Query(value = "SELECT valid_report_count FROM users WHERE id = :userId", nativeQuery = true)
    int findValidReportCount(@Param("userId") Long userId);

    /**
     * 상태만 원자적으로 바꾼다(ADMIN-04·05·10·11). {@code User.warn()}처럼 엔티티를
     * 메모리에서 바꾸고 더티 체킹으로 flush하면, {@code @DynamicUpdate}가 없어 Hibernate가
     * 매핑된 모든 컬럼을 다시 쓰기 때문에 그 사이 다른 트랜잭션이 {@link #increaseValidReportCount}로
     * 원자적으로 늘려둔 값을 낡은 값으로 덮어써 버릴 수 있다 — 상태 변경은 이 원자적
     * UPDATE로만 한다.
     */
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :userId")
    void updateStatus(@Param("userId") Long userId, @Param("status") UserStatus status);
}
