package com.evergarden.evergardenbackend.report.repository;

import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 대시보드의 {@code pendingReportCount}(ADMIN-03). */
    long countByStatus(ReportStatus status);
}
