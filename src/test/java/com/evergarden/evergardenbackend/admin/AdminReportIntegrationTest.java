package com.evergarden.evergardenbackend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportReason;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.entity.ReportTargetType;
import com.evergarden.evergardenbackend.report.repository.ReportRepository;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 Postgres로 신고 목록 정렬(새 CASE WHEN 쿼리)부터 상세 조회·판정까지 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
class AdminReportIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired PostRepository postRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired EntityManager entityManager;

    private String adminToken() {
        Admin admin = adminRepository.save(Admin.builder()
                .loginId("ops-" + System.nanoTime()).passwordHash("hash").name("담당자").build());
        return tokenProvider.issueAccessToken(admin.getId(), Role.ADMIN);
    }

    @Test
    @DisplayName("미처리가 먼저, 그다음 접수 순서대로 나온다 — 새 CASE WHEN 정렬이 실제로 동작하는지 확인")
    void 목록_미처리우선_접수순() throws Exception {
        long unique = System.nanoTime() % 100_000;
        User target = userRepository.save(User.builder().nickname("대상자" + unique).build());
        User reporter1 = userRepository.save(User.builder().nickname("신고자a" + unique).build());
        User reporter2 = userRepository.save(User.builder().nickname("신고자b" + unique).build());
        User reporter3 = userRepository.save(User.builder().nickname("신고자c" + unique).build());
        Post post = postRepository.save(Post.builder()
                .author(target).content("내용").shareType(ShareType.ARCHIVE).build());

        Report old = reportRepository.save(Report.builder()
                .reporter(reporter1).targetType(ReportTargetType.POST).targetId(post.getId())
                .targetUser(target).reason(ReportReason.ABUSE).detail(null).build());
        ReflectionTestUtils.setField(old, "createdAt", LocalDateTime.now().minusDays(2));
        Report reviewed = reportRepository.save(Report.builder()
                .reporter(reporter2).targetType(ReportTargetType.POST).targetId(post.getId())
                .targetUser(target).reason(ReportReason.SPAM).detail(null).build());
        ReflectionTestUtils.setField(reviewed, "createdAt", LocalDateTime.now().minusDays(3));
        reviewed.reject(null, LocalDateTime.now(), "근거 부족");
        Report recent = reportRepository.save(Report.builder()
                .reporter(reporter3).targetType(ReportTargetType.POST).targetId(post.getId())
                .targetUser(target).reason(ReportReason.OBSCENE).detail(null).build());
        ReflectionTestUtils.setField(recent, "createdAt", LocalDateTime.now().minusDays(1));

        // 이 테이블은 여러 통합 테스트가 공유해서 전체 개수를 장담할 수 없다 — 최대 페이지를
        // 받아 이번 테스트가 만든 3건만 걸러 상대 순서(미처리 먼저 · 접수순)를 확인한다.
        MvcResult result = mvc.perform(get("/admin/reports?size=50")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = jsonMapper.readTree(result.getResponse().getContentAsString()).path("data");
        List<Long> myOrder = List.of(old.getId(), recent.getId(), reviewed.getId());
        List<Long> actualOrder = new ArrayList<>();
        data.forEach(node -> {
            long reportId = node.path("reportId").asLong();
            if (myOrder.contains(reportId)) {
                actualOrder.add(reportId);
            }
        });
        Assertions.assertThat(actualOrder).containsExactlyElementsOf(myOrder);
    }

    @Test
    @DisplayName("상세 조회 → 유효 판정까지 실제 DB로 — 대상 게시물 내용이 그대로 담긴다")
    void 상세조회_판정_흐름() throws Exception {
        long unique = System.nanoTime() % 100_000;
        User target = userRepository.save(User.builder().nickname("대상자d" + unique).build());
        User reporter = userRepository.save(User.builder().nickname("신고자d" + unique).build());
        Post post = postRepository.save(Post.builder()
                .author(target).content("문제의 내용").shareType(ShareType.ARCHIVE).build());
        Report report = reportRepository.save(Report.builder()
                .reporter(reporter).targetType(ReportTargetType.POST).targetId(post.getId())
                .targetUser(target).reason(ReportReason.ABUSE).detail("상세 설명").build());
        String token = adminToken();

        mvc.perform(get("/admin/reports/" + report.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetContent").value("문제의 내용"))
                .andExpect(jsonPath("$.data.targetDeleted").value(false));

        mvc.perform(patch("/admin/reports/" + report.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"VALID","note":"확인함"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetUserValidReportCount").value(1))
                .andExpect(jsonPath("$.data.appliedSanction.type").value("WARNING"))
                .andExpect(jsonPath("$.data.report.status").value("VALID"));

        mvc.perform(patch("/admin/reports/" + report.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_REVIEWED"));
    }

    @Test
    @DisplayName("이미 차단된 회원이 뒤늦게 1번째 유효 판정을 받아도 자동 경고가 차단을 풀지 않는다(ADR-033)")
    void 차단된회원_뒤늦은_첫유효판정_차단유지() throws Exception {
        long unique = System.nanoTime() % 100_000;
        User target = userRepository.save(User.builder().nickname("대상자e" + unique).build());
        User reporter = userRepository.save(User.builder().nickname("신고자e" + unique).build());
        Post post = postRepository.save(Post.builder()
                .author(target).content("내용").shareType(ShareType.ARCHIVE).build());
        Report report = reportRepository.save(Report.builder()
                .reporter(reporter).targetType(ReportTargetType.POST).targetId(post.getId())
                .targetUser(target).reason(ReportReason.ABUSE).detail(null).build());
        String token = adminToken();

        mvc.perform(post("/admin/users/" + target.getId() + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"약관 위반"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(patch("/admin/reports/" + report.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"VALID"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedSanction.type").value("WARNING"));
        entityManager.clear();

        mvc.perform(get("/admin/users/" + target.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));
    }
}
