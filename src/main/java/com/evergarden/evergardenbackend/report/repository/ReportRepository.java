package com.evergarden.evergardenbackend.report.repository;

import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 대시보드의 {@code pendingReportCount}(ADMIN-03). */
    long countByStatus(ReportStatus status);

    /**
     * 신고 목록(ADMIN-14). {@code status}가 없으면 전체를 미처리(PENDING) 먼저,
     * 접수 순서대로 반환한다. {@code status}가 있으면 그 상태만 접수 순서대로.
     */
    @Query("""
            SELECT r FROM Report r
            WHERE (:status IS NULL OR r.status = :status)
            ORDER BY CASE WHEN r.status = com.evergarden.evergardenbackend.report.entity.ReportStatus.PENDING
                          THEN 0 ELSE 1 END,
                     r.createdAt ASC
            """)
    Page<Report> search(@Param("status") ReportStatus status, Pageable pageable);
}
