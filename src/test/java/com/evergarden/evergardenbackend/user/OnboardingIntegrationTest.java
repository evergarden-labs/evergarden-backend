package com.evergarden.evergardenbackend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 PostgreSQL로 가입(자동 닉네임)→건너뛰기·초기설정, 닉네임 유니크 제약까지 확인한다.
 * 마이페이지(MY-01)가 아직 없어 조회까지는 이어가지 못한다 — 그 부분은 DB에서 직접 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
class OnboardingIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("가입 시 자동 생성된 닉네임 그대로 건너뛰면 온보딩만 완료된다")
    void 건너뛰기_흐름() throws Exception {
        User user = userRepository.save(User.builder().nickname("여행자1111").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        mvc.perform(post("/users/me/onboarding/skip").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("여행자1111"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isOnboardingCompleted()).isTrue();
        assertThat(reloaded.getNickname()).isEqualTo("여행자1111");
    }

    @Test
    @DisplayName("초기 설정으로 닉네임을 바꾸면 그 값으로 온보딩이 완료된다")
    void 초기설정_흐름() throws Exception {
        User user = userRepository.save(User.builder().nickname("여행자2222").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        mvc.perform(patch("/users/me/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"진짜닉네임"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("진짜닉네임"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getNickname()).isEqualTo("진짜닉네임");
        assertThat(reloaded.isOnboardingCompleted()).isTrue();
    }

    @Test
    @DisplayName("이미 쓰이고 있는 닉네임으로 초기 설정하면 NICKNAME_DUPLICATED — 실제 유니크 제약으로 확인")
    void 초기설정_닉네임중복() throws Exception {
        userRepository.save(User.builder().nickname("먼저씀").build());
        User user = userRepository.save(User.builder().nickname("여행자3333").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        mvc.perform(patch("/users/me/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"먼저씀"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
    }
}
