package com.evergarden.evergardenbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportReason;
import com.evergarden.evergardenbackend.report.entity.ReportTargetType;
import com.evergarden.evergardenbackend.report.repository.ReportRepository;
import com.evergarden.evergardenbackend.report.repository.SanctionRepository;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 같은 회원을 겨냥한 서로 다른 신고 3건이 정확히 동시에 유효 판정되어도
 * {@code validReportCount}가 정확히 3까지 오르고 차단이 딱 한 번만 걸리는지 확인한다.
 *
 * <p>{@code User.increaseValidReportCount()}는 엔티티를 메모리에서 증가시킨 뒤 더티
 * 체킹으로 flush하는 방식이라({@code UserGardenObjectRepository.tryGrow()}가 이미
 * 겪은 것과 같은 종류의 경합) 원자적 {@code UPDATE} 없이는 손실 업데이트가 날 수 있다
 * — {@code RegionVisitConcurrencyTest}와 같은 이유로 클래스에 {@code @Transactional}을
 * 걸지 않는다(걸면 모든 스레드가 테스트 스레드의 트랜잭션에 묶여 경합 자체가 안 생긴다).
 */
@AutoConfigureMockMvc
class AdminReportConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 3;

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired PostRepository postRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired SanctionRepository sanctionRepository;

    @Test
    @DisplayName("같은 회원을 겨냥한 신고 3건을 동시에 유효 판정해도 누적은 정확히 3, 차단은 한 번만")
    void 동시_유효판정_손실없이_3까지() throws Exception {
        User targetUser = userRepository.save(User.builder().nickname("동시성대상자").build());
        Post post = postRepository.save(Post.builder()
                .author(targetUser).content("내용").shareType(ShareType.ARCHIVE).build());
        Admin admin = adminRepository.save(Admin.builder()
                .loginId("ops-" + System.nanoTime()).passwordHash("hash").name("담당자").build());
        String adminToken = tokenProvider.issueAccessToken(admin.getId(), Role.ADMIN);

        List<Long> reportIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            User reporter = userRepository.save(User.builder().nickname("신고자" + i).build());
            Report report = reportRepository.save(Report.builder()
                    .reporter(reporter).targetType(ReportTargetType.POST).targetId(post.getId())
                    .targetUser(targetUser).reason(ReportReason.ABUSE).detail(null).build());
            reportIds.add(report.getId());
        }

        fireConcurrently(reportId -> mvc.perform(patch("/admin/reports/" + reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"VALID"}
                                """))
                .andReturn(), reportIds);

        User reloaded = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(reloaded.getValidReportCount()).isEqualTo(3);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.BLOCKED);
        // 1회째(WARNING)·3회째(BLOCK) 딱 두 번만 제재가 걸려야 한다 — 손실 업데이트가 있었다면
        // 어느 스레드가 몇 번째로 커밋됐는지 꼬여 같은 임계값에서 중복으로 걸리거나 아예 안 걸릴 수 있다.
        assertThat(sanctionRepository.findByUser_IdOrderByCreatedAtDesc(targetUser.getId()))
                .extracting(s -> s.getType().name())
                .containsExactlyInAnyOrder("WARNING", "BLOCK");
    }

    private void fireConcurrently(ThrowingConsumer<Long> request, List<Long> reportIds) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        for (Long reportId : reportIds) {
            Callable<Void> task = () -> {
                ready.countDown();
                start.await();
                request.accept(reportId);
                return null;
            };
            futures.add(executor.submit(task));
        }

        ready.await();
        start.countDown();

        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }
}
