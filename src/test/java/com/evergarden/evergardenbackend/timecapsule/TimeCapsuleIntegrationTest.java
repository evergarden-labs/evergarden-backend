package com.evergarden.evergardenbackend.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 PostgreSQL·스프링 컨텍스트로 봉인→목록 조회→위치 조건 충족→열기→다시 조회까지
 * 전체 흐름을 확인한다. 가장 조심해야 할 지점(봉인 상태에서 내용이 새면 안 됨)을
 * 목 없이 실제 DB 왕복으로 검증하는 게 목적이다.
 *
 * <p>{@code @Transactional}로 각 테스트를 롤백한다({@link com.evergarden.evergardenbackend.media.MediaIntegrationTest}와 같은 이유).
 */
@AutoConfigureMockMvc
@Transactional
class TimeCapsuleIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired TimeCapsuleRepository timeCapsuleRepository;

    String accessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder().nickname("테스터").build());
        accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);
    }

    @Test
    @DisplayName("봉인→목록 조회(내용 안 샘)→위치 조건 충족→열기→다시 조회(내용 나옴)까지 전체 흐름이 돈다")
    void 위치조건_캡슐_전체흐름() throws Exception {
        MvcResult createResult = mvc.perform(post("/time-capsules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"한라산 기억","content":"그날의 기억","unlockType":"LOCATION",
                                 "unlockLat":33.3617,"unlockLng":126.5292,"unlockRadiusMeters":500,"placeName":"한라산"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SEALED"))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andReturn();
        Long capsuleId = capsuleId(createResult);

        mvc.perform(get("/time-capsules/" + capsuleId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SEALED"))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.media").isEmpty());

        mvc.perform(get("/time-capsules").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].capsuleId").value(capsuleId))
                .andExpect(jsonPath("$.data[0].status").value("SEALED"))
                .andExpect(jsonPath("$.data[0].content").doesNotExist());

        mvc.perform(post("/time-capsules/" + capsuleId + "/open").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CAPSULE_NOT_UNLOCKABLE"));

        mvc.perform(post("/time-capsules/unlock-check")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":33.3617,"lng":126.5292}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].capsuleId").value(capsuleId))
                .andExpect(jsonPath("$.data[0].status").value("UNLOCKABLE"));

        mvc.perform(get("/time-capsules/" + capsuleId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNLOCKABLE"))
                .andExpect(jsonPath("$.data.content").doesNotExist());

        mvc.perform(post("/time-capsules/" + capsuleId + "/open").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPENED"))
                .andExpect(jsonPath("$.data.content").value("그날의 기억"))
                .andExpect(jsonPath("$.data.openedAt").exists());

        mvc.perform(get("/time-capsules/" + capsuleId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPENED"))
                .andExpect(jsonPath("$.data.content").value("그날의 기억"));

        mvc.perform(post("/time-capsules/" + capsuleId + "/open").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CAPSULE_ALREADY_OPENED"));
    }

    @Test
    @DisplayName("삭제하면 실제로 행이 지워져서 조회하면 404다")
    void 삭제하면_행이_사라진다() throws Exception {
        MvcResult createResult = mvc.perform(post("/time-capsules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","content":"내용","unlockType":"DATE","unlockDate":"2099-01-01"}
                                """))
                .andReturn();
        Long capsuleId = capsuleId(createResult);

        mvc.perform(delete("/time-capsules/" + capsuleId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(timeCapsuleRepository.findById(capsuleId)).isEmpty();
        mvc.perform(get("/time-capsules/" + capsuleId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TIME_CAPSULE_NOT_FOUND"));
    }

    private Long capsuleId(MvcResult result) throws Exception {
        JsonNode root = jsonMapper.readTree(result.getResponse().getContentAsString());
        return root.at("/data/capsuleId").asLong();
    }
}
