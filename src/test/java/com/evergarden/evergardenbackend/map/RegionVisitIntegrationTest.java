package com.evergarden.evergardenbackend.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import com.evergarden.evergardenbackend.garden.entity.GardenObjectType;
import com.evergarden.evergardenbackend.garden.repository.GardenObjectRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.map.repository.RegionVisitRepository;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 PostgreSQL·스프링 컨텍스트로 지역 인증→정원 보상→재인증 쿨다운→정원/지도 반영까지
 * 전체 흐름을 확인한다. {@link TourApiClient}만 목으로 두고(실제 콘텐츠랩을 때리지 않는다,
 * {@code MediaIntegrationTest}가 S3를 목으로 두는 것과 같은 이유) 나머지는 전부 진짜 빈으로 엮는다.
 *
 * <p>{@code @Transactional}로 각 테스트를 롤백한다.
 */
@AutoConfigureMockMvc
@Transactional
class RegionVisitIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired GardenObjectRepository gardenObjectRepository;
    @Autowired RegionVisitRepository regionVisitRepository;

    @MockitoBean TourApiClient tourApiClient;

    String accessToken;
    User user;
    Region jongno;
    Region jung;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().nickname("테스터").build());
        accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        Region seoul = regionRepository.save(Region.builder()
                .code("11").level(RegionLevel.SIDO).name("서울특별시")
                .centerLat(new BigDecimal("37.5665")).centerLng(new BigDecimal("126.9780"))
                .syncedAt(LocalDateTime.now()).build());
        jongno = regionRepository.save(Region.builder()
                .code("110").parent(seoul).level(RegionLevel.SIGUNGU).name("종로구")
                .centerLat(new BigDecimal("37.5729")).centerLng(new BigDecimal("126.9794"))
                .syncedAt(LocalDateTime.now()).build());
        jung = regionRepository.save(Region.builder()
                .code("140").parent(seoul).level(RegionLevel.SIGUNGU).name("중구")
                .centerLat(new BigDecimal("37.5641")).centerLng(new BigDecimal("126.9979"))
                .syncedAt(LocalDateTime.now()).build());
    }

    private void givenNearby(String signguCode) {
        given(tourApiClient.fetchNearby(new BigDecimal("37.5729"), new BigDecimal("126.9794"), 2_000))
                .willReturn(List.of(new LocationBasedItem("c1", "12", "경복궁", "11", signguCode)));
    }

    private void givenNearbyAt(BigDecimal lat, BigDecimal lng, String signguCode) {
        given(tourApiClient.fetchNearby(lat, lng, 2_000))
                .willReturn(List.of(new LocationBasedItem("c2", "12", "명동성당", "11", signguCode)));
    }

    @Test
    @DisplayName("처음 인증→해금→같은 지역 재인증 쿨다운→정원·지도에 반영된다")
    void 인증부터_정원반영까지_전체흐름() throws Exception {
        GardenObject tree = gardenObjectRepository.save(GardenObject.builder()
                .region(jongno).name("종로 은행나무").type(GardenObjectType.PLANT).maxStage((short) 3).build());
        givenNearby("110");

        MvcResult first = mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5729,"lng":126.9794,"accuracyMeters":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFirstVisit").value(true))
                .andExpect(jsonPath("$.data.rewardStatus").value("UNLOCKED"))
                .andExpect(jsonPath("$.data.reward.gardenObject.gardenObjectId").value(tree.getId()))
                .andReturn();
        Long visitId = visitId(first);

        mvc.perform(get("/region-visits/" + visitId + "/reward").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardStatus").value("UNLOCKED"))
                .andExpect(jsonPath("$.data.currentStage").value(1));

        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5729,"lng":126.9794,"accuracyMeters":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFirstVisit").value(false))
                .andExpect(jsonPath("$.data.rewardStatus").value("COOLDOWN"))
                .andExpect(jsonPath("$.data.reward.nextAvailableAt").exists());

        mvc.perform(get("/garden").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unlockedCount").value(1))
                .andExpect(jsonPath("$.data.objects[0].stage").value(1));

        MvcResult listResult = mvc.perform(get("/users/me/regions?level=SIGUNGU")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jongnoStatus = findRegionStatus(listResult, "110");
        assertThat(jongnoStatus.at("/visited").asBoolean()).isTrue();
        assertThat(jongnoStatus.at("/visitCount").asInt()).isEqualTo(2);

        mvc.perform(get("/regions/110").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visited").value(true))
                .andExpect(jsonPath("$.data.visitCount").value(2))
                .andExpect(jsonPath("$.data.availableGardenObjects[0].gardenObjectId").value(tree.getId()));
    }

    @Test
    @DisplayName("서로 다른 지역을 인증하면 각 지역 오브젝트가 독립적으로 해금·관리된다")
    void 서로다른지역_독립적으로_해금() throws Exception {
        GardenObject jongnoTree = gardenObjectRepository.save(GardenObject.builder()
                .region(jongno).name("종로 은행나무").type(GardenObjectType.PLANT).maxStage((short) 3).build());
        GardenObject jungTree = gardenObjectRepository.save(GardenObject.builder()
                .region(jung).name("중구 소나무").type(GardenObjectType.PLANT).maxStage((short) 3).build());
        givenNearby("110");
        givenNearbyAt(new BigDecimal("37.5641"), new BigDecimal("126.9979"), "140");

        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5729,"lng":126.9794,"accuracyMeters":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFirstVisit").value(true))
                .andExpect(jsonPath("$.data.rewardStatus").value("UNLOCKED"))
                .andExpect(jsonPath("$.data.reward.gardenObject.gardenObjectId").value(jongnoTree.getId()));

        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5641,"lng":126.9979,"accuracyMeters":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFirstVisit").value(true))
                .andExpect(jsonPath("$.data.rewardStatus").value("UNLOCKED"))
                .andExpect(jsonPath("$.data.reward.gardenObject.gardenObjectId").value(jungTree.getId()));

        mvc.perform(get("/garden").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unlockedCount").value(2))
                .andExpect(jsonPath("$.data.objects[0].stage").value(1))
                .andExpect(jsonPath("$.data.objects[1].stage").value(1));
    }

    @Test
    @DisplayName("정확도가 100m를 넘으면 거절되고 방문 기록이 안 남는다")
    void 정확도초과_기록안남음() throws Exception {
        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5729,"lng":126.9794,"accuracyMeters":150}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("LOCATION_ACCURACY_TOO_LOW"));

        assertThat(regionVisitRepository.aggregateByUser(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("판정된 지역과 요청의 regionCode가 다르면 LOCATION_MISMATCH")
    void 지역코드_불일치() throws Exception {
        givenNearby("110");

        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5729,"lng":126.9794,"accuracyMeters":10,"regionCode":"140"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("LOCATION_MISMATCH"));
    }

    @Test
    @DisplayName("콘텐츠랩 호출 자체가 실패하면 TOUR_API_UNAVAILABLE")
    void 콘텐츠랩_호출실패() throws Exception {
        given(tourApiClient.fetchNearby(new BigDecimal("37.5729"), new BigDecimal("126.9794"), 2_000))
                .willThrow(new RestClientException("연결 실패"));

        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5729,"lng":126.9794,"accuracyMeters":10}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("TOUR_API_UNAVAILABLE"));
    }

    @Test
    @DisplayName("반경 안에 관광지가 없으면 REGION_NOT_DETERMINED")
    void 판정실패() throws Exception {
        given(tourApiClient.fetchNearby(new BigDecimal("37.5729"), new BigDecimal("126.9794"), 2_000))
                .willReturn(List.of());

        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5729,"lng":126.9794,"accuracyMeters":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REGION_NOT_DETERMINED"));
    }

    @Test
    @DisplayName("남의 방문 기록을 조회하면 403")
    void 보상조회_남의방문() throws Exception {
        givenNearby("110");
        MvcResult result = mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5729,"lng":126.9794,"accuracyMeters":10}
                                """))
                .andReturn();
        Long visitId = visitId(result);

        User other = userRepository.save(User.builder().nickname("다른사람").build());
        String otherToken = tokenProvider.issueAccessToken(other.getId(), Role.USER);

        mvc.perform(get("/region-visits/" + visitId + "/reward").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }

    private Long visitId(MvcResult result) throws Exception {
        JsonNode root = jsonMapper.readTree(result.getResponse().getContentAsString());
        return root.at("/data/visitId").asLong();
    }

    /**
     * 목록에서 지역코드로 하나를 찾는다. {@code $.data[0]}처럼 배열 순서에 기대면
     * 다른 테스트(특히 {@code @Transactional} 없이 도는 {@code RegionVisitConcurrencyTest})가
     * 커밋해 둔 지역까지 같이 잡혀서 순서가 흔들릴 수 있다 — 그래서 코드로 직접 찾는다.
     */
    private JsonNode findRegionStatus(MvcResult result, String regionCode) throws Exception {
        JsonNode data = jsonMapper.readTree(result.getResponse().getContentAsString()).at("/data");
        for (JsonNode node : data) {
            if (regionCode.equals(node.at("/region/code").asText())) {
                return node;
            }
        }
        throw new AssertionError("목록에서 지역코드를 못 찾음: " + regionCode);
    }
}
