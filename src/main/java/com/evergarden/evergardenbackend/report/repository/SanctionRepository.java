package com.evergarden.evergardenbackend.report.repository;

import com.evergarden.evergardenbackend.report.entity.Sanction;
import com.evergarden.evergardenbackend.report.entity.SanctionType;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {

    /** 가장 최근 제재 하나. 로그인 시 차단 사유·시점을 안내하는 데 쓴다(AUTH-06). */
    Optional<Sanction> findTopByUser_IdAndTypeOrderByCreatedAtDesc(Long userId, SanctionType type);
}
