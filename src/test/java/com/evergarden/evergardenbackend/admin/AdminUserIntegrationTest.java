package com.evergarden.evergardenbackend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 Postgres로 회원 목록 검색({@code UserRepository.search}의 새 JPQL)부터
 * 경고·차단·차단 해제까지 전체 흐름을 확인한다.
 *
 * <p>테스트 클래스 전체가 트랜잭션 하나로 묶여 있어({@code @Transactional}), 여러
 * {@code mvc.perform()} 호출이 같은 영속성 컨텍스트를 공유한다. 원자적 벌크 UPDATE
 * ({@code warnIfNotBlocked} 등)는 엔티티를 안 거치고 DB만 바로 바꾸므로, 그다음
 * 호출이 앞서 캐시된 낡은 엔티티를 그대로 돌려주지 않게 {@link #entityManager}로
 * 매번 비워준다 — 실제 운영에서는 요청마다 트랜잭션이 새로 열려 저절로 해결되는 문제다.
 */
@AutoConfigureMockMvc
@Transactional
class AdminUserIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired EntityManager entityManager;

    private String adminToken() {
        Admin admin = adminRepository.save(Admin.builder()
                .loginId("ops-" + System.nanoTime()).passwordHash("hash").name("담당자").build());
        return tokenProvider.issueAccessToken(admin.getId(), Role.ADMIN);
    }

    @Test
    @DisplayName("닉네임 부분 일치로 검색된다 — 새로 추가한 JPQL이 실제로 동작하는지 확인")
    void 목록_키워드검색() throws Exception {
        userRepository.save(User.builder().nickname("여행자바다").build());
        userRepository.save(User.builder().nickname("여행자산").build());
        userRepository.save(User.builder().nickname("다른사람").build());
        String token = adminToken();

        mvc.perform(get("/admin/users?keyword=여행자").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("경고 → 차단 → 중복 차단 거절 → 차단 해제 → 다시 해제해도 멱등까지 전체 흐름")
    void 경고_차단_해제_흐름() throws Exception {
        User user = userRepository.save(User.builder().nickname("대상자").build());
        String token = adminToken();

        mvc.perform(post("/admin/users/" + user.getId() + "/warnings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"정책 위반"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WARNING"))
                .andExpect(jsonPath("$.data.issuedByAdminName").value("담당자"));
        entityManager.clear();

        mvc.perform(get("/admin/users/" + user.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WARNED"))
                .andExpect(jsonPath("$.data.sanctions.length()").value(1));

        mvc.perform(post("/admin/users/" + user.getId() + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"약관 위반"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("BLOCK"));

        mvc.perform(post("/admin/users/" + user.getId() + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"약관 위반"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_REQUEST"));

        mvc.perform(delete("/admin/users/" + user.getId() + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"이의 제기 수용"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sanctions.length()").value(3));

        mvc.perform(delete("/admin/users/" + user.getId() + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"실수로 다시 호출"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sanctions.length()").value(3));
    }

    @Test
    @DisplayName("차단된 회원에게 경고해도 차단이 풀리지 않는다 — warnIfNotBlocked의 WHERE절이 실제로 동작하는지 확인(ADR-033)")
    void 차단된회원_경고해도_차단유지() throws Exception {
        User user = userRepository.save(User.builder().nickname("차단대상자").build());
        String token = adminToken();

        mvc.perform(post("/admin/users/" + user.getId() + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"약관 위반"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/admin/users/" + user.getId() + "/warnings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"실수로 경고도 눌러봄"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WARNING"));
        entityManager.clear();

        mvc.perform(get("/admin/users/" + user.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.sanctions.length()").value(2));
    }
}
