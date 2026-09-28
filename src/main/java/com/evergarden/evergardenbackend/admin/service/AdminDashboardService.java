package com.evergarden.evergardenbackend.admin.service;

import com.evergarden.evergardenbackend.admin.dto.AdminDashboard;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.repository.ReportRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 대시보드 현황 숫자(ADMIN-03).
 *
 * <p>매번 실시간 {@code COUNT(*)}로 계산한다. 지금 데이터량에선 문제없고,
 * 커지면 그때 캐시나 집계 테이블을 고려한다 — 미리 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ArchiveRepository archiveRepository;
    private final TripRepository tripRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;

    public AdminDashboard getDashboard() {
        return new AdminDashboard(
                (int) userRepository.countByStatusNot(UserStatus.WITHDRAWN),
                (int) userRepository.countByCreatedAtGreaterThanEqual(LocalDate.now().atStartOfDay()),
                (int) reportRepository.countByStatus(ReportStatus.PENDING),
                (int) userRepository.countByStatusIn(List.of(UserStatus.WARNED, UserStatus.BLOCKED)),
                (int) archiveRepository.count(),
                (int) tripRepository.count(),
                (int) postRepository.countByStatus(PostStatus.ACTIVE));
    }
}
