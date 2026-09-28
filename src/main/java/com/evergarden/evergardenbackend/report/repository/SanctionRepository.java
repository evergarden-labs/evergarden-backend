package com.evergarden.evergardenbackend.report.repository;

import com.evergarden.evergardenbackend.report.entity.Sanction;
import com.evergarden.evergardenbackend.report.entity.SanctionType;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {

    /** 가장 최근 제재 하나. 로그인 시 차단 사유·시점을 안내하는 데 쓴다(AUTH-06). */
    Optional<Sanction> findTopByUser_IdAndTypeOrderByCreatedAtDesc(Long userId, SanctionType type);

    /** 관리자 회원 상세의 경고·차단·해제 이력(ADMIN-13). 최근 것이 앞에 온다. */
    List<Sanction> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
