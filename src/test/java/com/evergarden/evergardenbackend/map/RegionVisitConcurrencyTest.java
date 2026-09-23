package com.evergarden.evergardenbackend.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import com.evergarden.evergardenbackend.garden.entity.GardenObjectType;
import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;
import com.evergarden.evergardenbackend.garden.repository.GardenObjectRepository;
import com.evergarden.evergardenbackend.garden.repository.UserGardenObjectRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.LocationBasedItem;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 처음 해금(ADR-006, DB 유니크 제약)과 성장(《@Version》 낙관적 락)이 진짜 동시
 * 요청에서도 한 번만 반영되는지 확인한다. {@code CommunityConcurrencyTest}와 같은
 * 이유로 {@code @Transactional}을 클래스에 안 건다 — 걸면 모든 스레드가 테스트
 * 스레드의 트랜잭션에 묶여 경합 자체가 안 생긴다.
 */
@AutoConfigureMockMvc
class RegionVisitConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 10;

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired GardenObjectRepository gardenObjectRepository;
    @Autowired UserGardenObjectRepository userGardenObjectRepository;

    @MockitoBean TourApiClient tourApiClient;

    @Test
    @DisplayName("같은 사용자가 처음 방문하는 지역을 동시에 여러 번 인증해도 해금은 한 번만 된다(ADR-006)")
    void 동시_처음해금_하나만_성공() throws Exception {
        User user = userRepository.save(User.builder().nickname("동시성해금테스터").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        Region seoul = regionRepository.save(Region.builder()
                .code("11동시").level(RegionLevel.SIDO).name("서울특별시")
                .centerLat(new BigDecimal("37.5665")).centerLng(new BigDecimal("126.9780"))
                .syncedAt(LocalDateTime.now()).build());
        Region jongno = regionRepository.save(Region.builder()
                .code("110동시").parent(seoul).level(RegionLevel.SIGUNGU).name("종로구")
                .centerLat(new BigDecimal("37.5729")).centerLng(new BigDecimal("126.9794"))
                .syncedAt(LocalDateTime.now()).build());
        GardenObject tree = gardenObjectRepository.save(GardenObject.builder()
                .region(jongno).name("종로 은행나무").type(GardenObjectType.PLANT).maxStage((short) 3).build());

        given(tourApiClient.fetchNearby(new BigDecimal("37.5729"), new BigDecimal("126.9794"), 2_000))
                .willReturn(List.of(new LocationBasedItem("c1", "12", "경복궁", "11동시", "110동시")));

        List<JsonNode> responses = fireConcurrently(() -> {
            MvcResult result = mvc.perform(post("/region-visits")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"lat":37.5729,"lng":126.9794,"accuracyMeters":10}
                                    """))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            return jsonMapper.readTree(result.getResponse().getContentAsString()).at("/data");
        });

        List<String> rewardStatuses = responses.stream().map(r -> r.at("/rewardStatus").asText()).toList();
        assertThat(rewardStatuses).filteredOn("UNLOCKED"::equals).hasSize(1);
        assertThat(userGardenObjectRepository.findByUser_IdAndGardenObject_Id(user.getId(), tree.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("이미 해금한 오브젝트를 동시에 여러 번 인증해도 성장은 한 번만 된다(@Version)")
    void 동시_성장_하나만_성공() throws Exception {
        User user = userRepository.save(User.builder().nickname("동시성성장테스터").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        Region seoul = regionRepository.save(Region.builder()
                .code("12동시").level(RegionLevel.SIDO).name("부산광역시")
                .centerLat(new BigDecimal("35.1796")).centerLng(new BigDecimal("129.0756"))
                .syncedAt(LocalDateTime.now()).build());
        Region haeundae = regionRepository.save(Region.builder()
                .code("120동시").parent(seoul).level(RegionLevel.SIGUNGU).name("해운대구")
                .centerLat(new BigDecimal("35.1631")).centerLng(new BigDecimal("129.1637"))
                .syncedAt(LocalDateTime.now()).build());
        GardenObject tree = gardenObjectRepository.save(GardenObject.builder()
                .region(haeundae).name("해운대 해송").type(GardenObjectType.PLANT).maxStage((short) 5).build());
        UserGardenObject unlocked = UserGardenObject.builder()
                .user(user).gardenObject(tree).unlockedAt(LocalDateTime.now().minusDays(10)).build();
        ReflectionTestUtils.setField(unlocked, "lastGrownAt", LocalDateTime.now().minusDays(8));
        userGardenObjectRepository.save(unlocked);

        given(tourApiClient.fetchNearby(new BigDecimal("35.1631"), new BigDecimal("129.1637"), 2_000))
                .willReturn(List.of(new LocationBasedItem("c2", "12", "해운대해수욕장", "12동시", "120동시")));

        List<JsonNode> responses = fireConcurrently(() -> {
            MvcResult result = mvc.perform(post("/region-visits")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"lat":35.1631,"lng":129.1637,"accuracyMeters":10}
                                    """))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            return jsonMapper.readTree(result.getResponse().getContentAsString()).at("/data");
        });

        List<String> rewardStatuses = responses.stream().map(r -> r.at("/rewardStatus").asText()).toList();
        assertThat(rewardStatuses).filteredOn("GROWN"::equals).hasSize(1);
        assertThat(rewardStatuses).filteredOn("COOLDOWN"::equals).hasSize(THREADS - 1);

        // 경합에서 진 요청들도 실제 최신 stage(2)를 봐야 한다 — 자기가 처음 읽었던 낡은
        // 값(1)을 그대로 돌려받으면(Hibernate 1차 캐시 문제) 여기서 잡힌다.
        List<Integer> cooldownCurrentStages = responses.stream()
                .filter(r -> "COOLDOWN".equals(r.at("/rewardStatus").asText()))
                .map(r -> r.at("/reward/currentStage").asInt())
                .toList();
        assertThat(cooldownCurrentStages).allMatch(stage -> stage == 2);

        assertThat(userGardenObjectRepository.findByUser_IdAndGardenObject_Id(user.getId(), tree.getId()))
                .get().extracting(UserGardenObject::getStage).isEqualTo((short) 2);
    }

    private List<JsonNode> fireConcurrently(Callable<JsonNode> request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JsonNode>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return request.call();
            }));
        }

        ready.await();
        start.countDown();

        List<JsonNode> results = new ArrayList<>();
        for (Future<JsonNode> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return results;
    }
}
