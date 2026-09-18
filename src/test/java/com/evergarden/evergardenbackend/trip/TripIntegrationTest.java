package com.evergarden.evergardenbackend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 PostgreSQL·스프링 컨텍스트로 일정 생성부터 장소 담기·재배치·동선·가져오기까지
 * 전체 흐름이 실제로 엮여 도는지 확인한다. 시큐리티 필터·검증·서비스·리포지토리 전부
 * 진짜 빈이다 — 외부 TourAPI만 관련이 없어 모킹할 것도 없다(이미 DB에 넣어 둔
 * {@code places}만 쓴다).
 */
@AutoConfigureMockMvc
@Transactional
class TripIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired TripRepository tripRepository;
    @Autowired PostRepository postRepository;

    User owner;
    String accessToken;
    Region seoul;
    Region jung;
    Place namsan;
    Place deoksugung;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder().nickname("테스터").build());
        accessToken = tokenProvider.issueAccessToken(owner.getId(), Role.USER);

        seoul = regionRepository.save(region("11", RegionLevel.SIDO, null, "서울특별시"));
        jung = regionRepository.save(region("11140", RegionLevel.SIGUNGU, seoul, "중구"));
        namsan = placeRepository.save(place("t-namsan", "남산공원", 37.5556, 126.9922, jung));
        deoksugung = placeRepository.save(place("t-deoksugung", "덕수궁", 37.5651, 126.9766, jung));
    }

    private Region region(String code, RegionLevel level, Region parent, String name) {
        return Region.builder().code(code).level(level).name(name)
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO).syncedAt(LocalDateTime.now())
                .parent(parent).build();
    }

    private Place place(String contentId, String title, double lat, double lng, Region region) {
        return Place.builder().contentId(contentId).contentTypeId("12").title(title)
                .lat(BigDecimal.valueOf(lat)).lng(BigDecimal.valueOf(lng)).region(region)
                .syncedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("생성 → 장소 담기 → 재배치 → 동선·자동배치 조회까지 전체 흐름이 돈다")
    void 생성부터_동선조회까지() throws Exception {
        MvcResult createResult = mvc.perform(post("/trips")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"서울 여행","startDate":"2026-03-01","endDate":"2026-03-01","regionCodes":["11"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andReturn();
        Long tripId = at(createResult, "/data/tripId");

        MvcResult addFirst = mvc.perform(post("/trips/" + tripId + "/places")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + namsan.getId() + ",\"dayNumber\":1}"))
                .andExpect(status().isOk())
                .andReturn();
        Long firstTripPlaceId = at(addFirst, "/data/tripPlaceId");

        MvcResult addSecond = mvc.perform(post("/trips/" + tripId + "/places")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + deoksugung.getId() + ",\"dayNumber\":1,\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andReturn();
        Long secondTripPlaceId = at(addSecond, "/data/tripPlaceId");

        // 끼워넣기로 남산이 2번으로 밀렸어야 한다(PLAN-02)
        mvc.perform(get("/trips/" + tripId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].places[0].tripPlaceId").value(secondTripPlaceId))
                .andExpect(jsonPath("$.data.days[0].places[1].tripPlaceId").value(firstTripPlaceId));

        // 순서를 통째로 되돌린다
        mvc.perform(put("/trips/" + tripId + "/places/order")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                    {"tripPlaceId":%d,"dayNumber":1,"sortOrder":1},
                                    {"tripPlaceId":%d,"dayNumber":1,"sortOrder":2}
                                ]}
                                """.formatted(firstTripPlaceId, secondTripPlaceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].places[0].tripPlaceId").value(firstTripPlaceId));

        // 동선 조회 — 실제 좌표로 계산한 거리가 나와야 한다
        mvc.perform(get("/trips/" + tripId + "/route")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].points.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].totalDistanceMeters").isNumber())
                .andExpect(jsonPath("$.data.boundingBox").exists());

        // 자동 배치는 제안만 하고 저장하지 않는다(ADR-031)
        mvc.perform(post("/trips/" + tripId + "/auto-arrange")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.currentDistanceMeters").isNumber());

        // 자동 배치 호출 후에도 실제 저장된 순서는 그대로여야 한다(ADR-031)
        mvc.perform(get("/trips/" + tripId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].places[0].tripPlaceId").value(firstTripPlaceId));

        // 주변 추천 — 담긴 두 장소는 결과에서 빠져야 한다
        mvc.perform(get("/trips/" + tripId + "/nearby-places")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.placeId == " + namsan.getId() + ")]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.placeId == " + deoksugung.getId() + ")]").doesNotExist());

        mvc.perform(delete("/trips/" + tripId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(tripRepository.findById(tripId)).isEmpty();
    }

    /**
     * 원본 삭제 후에도 복사본이 살아남는지는(ADR-005) 여기서는 확인하지 않는다 —
     * 한 트랜잭션 안에서 지우면 지운 트립을 참조하는 {@code post}가 같은 영속성
     * 컨텍스트에 남아 있어, DB의 {@code ON DELETE SET NULL}과 무관하게 Hibernate 자체의
     * 그래프 정합성 검사에 걸린다(실제 운영에서는 요청마다 세션이 독립적이라 벌어지지
     * 않는 문제다). 이 동작은 실제 서버를 띄워 원본을 지우고 복사본을 다시 조회하는
     * 방식으로 이미 라이브 검증했다 — 여기서는 가져오기 자체가 실제 보안·검증·DB
     * 스택을 통해 값을 제대로 복제하는지만 확인한다.
     */
    @Test
    @DisplayName("공유된 코스를 가져오면 값이 복제된 독립 일정이 만들어진다(ADR-005)")
    void 공유코스_가져오기() throws Exception {
        Trip original = tripRepository.save(Trip.builder().owner(owner).title("원본 코스")
                        .startDate(java.time.LocalDate.of(2026, 1, 1))
                        .endDate(java.time.LocalDate.of(2026, 1, 2)).build());
        Post post = postRepository.save(Post.builder().author(owner).content("코스 공유")
                .shareType(ShareType.COURSE).sharedTrip(original).build());

        mvc.perform(post("/posts/" + post.getId() + "/course/import")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2026-07-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").value(org.hamcrest.Matchers.not(original.getId())))
                .andExpect(jsonPath("$.data.title").value("원본 코스"))
                .andExpect(jsonPath("$.data.startDate").value("2026-07-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-07-02"))
                .andExpect(jsonPath("$.data.originTripId").value(original.getId()));
    }

    @Test
    @DisplayName("담긴 장소가 있는 날짜 밖으로 기간을 줄이면 거부된다(ADR-041)")
    void 기간축소_장소밀려남_거부() throws Exception {
        MvcResult createResult = mvc.perform(post("/trips")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"서울 3일","startDate":"2026-03-01","endDate":"2026-03-03","regionCodes":["11"]}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Long tripId = at(createResult, "/data/tripId");

        mvc.perform(post("/trips/" + tripId + "/places")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + namsan.getId() + ",\"dayNumber\":3}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/trips/" + tripId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endDate":"2026-03-02"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.details.displacedDays[0].dayNumber").value(3));
    }

    private Long at(MvcResult result, String path) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).at(path).asLong();
    }
}
