package com.evergarden.evergardenbackend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.admin.dto.AdminDashboard;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.repository.ReportRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 대시보드 현황 숫자(ADMIN-03)를 다룬다. */
class AdminDashboardServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final AdminDashboardService service = new AdminDashboardService(
            userRepository, archiveRepository, tripRepository, postRepository, reportRepository);

    @Test
    @DisplayName("각 지표를 맞는 필터 조건의 리포지토리 호출로 채운다")
    void 대시보드_조회() {
        given(userRepository.countByStatusNot(UserStatus.WITHDRAWN)).willReturn(100L);
        given(userRepository.countByCreatedAtGreaterThanEqual(any())).willReturn(3L);
        given(reportRepository.countByStatus(ReportStatus.PENDING)).willReturn(5L);
        given(userRepository.countByStatusIn(List.of(UserStatus.WARNED, UserStatus.BLOCKED))).willReturn(7L);
        given(archiveRepository.count()).willReturn(40L);
        given(tripRepository.count()).willReturn(50L);
        given(postRepository.countByStatus(PostStatus.ACTIVE)).willReturn(200L);

        AdminDashboard result = service.getDashboard();

        assertThat(result.totalUserCount()).isEqualTo(100);
        assertThat(result.newUserCountToday()).isEqualTo(3);
        assertThat(result.pendingReportCount()).isEqualTo(5);
        assertThat(result.sanctionedUserCount()).isEqualTo(7);
        assertThat(result.totalArchiveCount()).isEqualTo(40);
        assertThat(result.totalTripCount()).isEqualTo(50);
        assertThat(result.totalPostCount()).isEqualTo(200);
    }
}
