package com.evergarden.evergardenbackend.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.evergarden.evergardenbackend.place.client.dto.AreaBasedPage;
import com.evergarden.evergardenbackend.place.client.dto.AreaBasedSyncPage;
import com.evergarden.evergardenbackend.place.client.dto.DetailCommonItem;
import com.evergarden.evergardenbackend.place.client.dto.DetailImageItem;
import com.evergarden.evergardenbackend.place.client.dto.DetailIntroItem;
import com.evergarden.evergardenbackend.place.client.dto.RegionCode;
import com.evergarden.evergardenbackend.place.config.TourApiProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/** 법정동코드 조회({@code ldongCode2}) — 실제 HTTP 호출은 목 서버로 대체한다. */
class TourApiClientTest {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";

    private MockRestServiceServer mockServer;
    private TourApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TourApiClient(builder.build(), new TourApiProperties(BASE_URL, "test-service-key"));
    }

    @Test
    @DisplayName("시/도 목록을 받아온다 — lDongRegnCd 없이 호출한다")
    void 시도_목록() {
        mockServer.expect(requestTo(containsString("/ldongCode2")))
                .andExpect(queryParam("serviceKey", "test-service-key"))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                        "body":{"items":{"item":[
                            {"code":"11","name":"서울특별시"},
                            {"code":"26","name":"부산광역시"}
                        ]},"numOfRows":2,"pageNo":1,"totalCount":2}}}
                        """, MediaType.APPLICATION_JSON));

        List<RegionCode> provinces = client.fetchProvinces();

        assertThat(provinces).containsExactly(
                new RegionCode("11", "서울특별시"),
                new RegionCode("26", "부산광역시"));
    }

    @Test
    @DisplayName("시군구 목록은 lDongSignguCd/lDongSignguNm으로 매핑한다")
    void 시군구_목록() {
        mockServer.expect(requestTo(containsString("lDongRegnCd=11")))
                .andExpect(requestTo(containsString("lDongListYn=Y")))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                        "body":{"items":{"item":[
                            {"lDongRegnCd":"11","lDongRegnNm":"서울특별시","lDongSignguCd":"110","lDongSignguNm":"종로구"},
                            {"lDongRegnCd":"11","lDongRegnNm":"서울특별시","lDongSignguCd":"140","lDongSignguNm":"중구"}
                        ]},"numOfRows":2,"pageNo":1,"totalCount":2}}}
                        """, MediaType.APPLICATION_JSON));

        List<RegionCode> districts = client.fetchDistricts("11");

        assertThat(districts).containsExactly(
                new RegionCode("110", "종로구"),
                new RegionCode("140", "중구"));
    }

    @Test
    @DisplayName("증분 동기화는 지역·타입 없이 modifiedtime만으로 전국을 한 번에 부른다")
    void 증분동기화_전국조회() {
        mockServer.expect(requestTo(containsString("/areaBasedSyncList2")))
                .andExpect(queryParam("modifiedtime", "20260917"))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                        "body":{"items":{"item":[
                            {"contentid":"c1","contenttypeid":"12","title":"테스트 장소","addr1":"주소",
                             "tel":"","mapx":"126.97","mapy":"37.57","firstimage2":"",
                             "lDongRegnCd":"11","lDongSignguCd":"110","showflag":"1"}
                        ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}
                        """, MediaType.APPLICATION_JSON));

        AreaBasedSyncPage page = client.fetchSyncedPlaces("20260917", 1, 100);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).contentid()).isEqualTo("c1");
        assertThat(page.items().get(0).isVisible()).isTrue();
    }

    @Test
    @DisplayName("공통정보조회는 contentId만 보낸다 — contentTypeId를 넣으면 거부됨(실전 확인)")
    void 공통정보조회_contentId만() {
        mockServer.expect(requestTo(containsString("/detailCommon2")))
                .andExpect(queryParam("contentId", "12345"))
                .andExpect(requestTo(not(containsString("contentTypeId"))))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                        "body":{"items":{"item":[
                            {"contentid":"12345","overview":"멋진 관광지입니다"}
                        ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}
                        """, MediaType.APPLICATION_JSON));

        Optional<DetailCommonItem> result = client.fetchDetailCommon("12345");

        assertThat(result).isPresent();
        assertThat(result.get().overview()).isEqualTo("멋진 관광지입니다");
    }

    @Test
    @DisplayName("소개정보조회는 contentId와 contentTypeId를 같이 보낸다")
    void 소개정보조회_둘다보냄() {
        mockServer.expect(requestTo(containsString("/detailIntro2")))
                .andExpect(queryParam("contentId", "12345"))
                .andExpect(queryParam("contentTypeId", "12"))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                        "body":{"items":{"item":[
                            {"contentid":"12345","usetime":"09:00~18:00","restdate":"매주 월요일"}
                        ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}
                        """, MediaType.APPLICATION_JSON));

        Optional<DetailIntroItem> result = client.fetchDetailIntro("12345", "12");

        assertThat(result).isPresent();
        assertThat(result.get().resolveUseTime()).isEqualTo("09:00~18:00");
        assertThat(result.get().resolveRestDate()).isEqualTo("매주 월요일");
    }

    @Test
    @DisplayName("관광사진정보조회는 사진 목록을 그대로 돌려준다")
    void 관광사진정보조회() {
        mockServer.expect(requestTo(containsString("/detailImage2")))
                .andExpect(queryParam("contentId", "12345"))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                        "body":{"items":{"item":[
                            {"contentid":"12345","originimgurl":"http://example.com/1.jpg"},
                            {"contentid":"12345","originimgurl":"http://example.com/2.jpg"}
                        ]},"numOfRows":2,"pageNo":1,"totalCount":2}}}
                        """, MediaType.APPLICATION_JSON));

        List<DetailImageItem> images = client.fetchDetailImages("12345");

        assertThat(images).extracting(DetailImageItem::originimgurl)
                .containsExactly("http://example.com/1.jpg", "http://example.com/2.jpg");
    }

    /**
     * TourAPI는 결과가 없으면 {@code items}가 객체가 아니라 빈 문자열로 온다 — 실전에서
     * {@code areaBasedList2}로 결과 없는 지역·타입 조합을 부를 때 크래시로 확인된 문제라
     * {@code ExternalApiConfig}와 똑같이 코덱을 관용도 있게 설정해 회귀를 잠근다.
     */
    @Test
    @DisplayName("결과가 없으면 items가 빈 문자열로 와도 빈 목록으로 처리한다")
    void 결과없으면_빈문자열_items도_처리() {
        JsonMapper tourApiJsonMapper = JsonMapper.builder()
                .withCoercionConfig(LogicalType.POJO,
                        cfg -> cfg.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull))
                .build();
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL)
                .configureMessageConverters(converters ->
                        converters.withJsonConverter(new JacksonJsonHttpMessageConverter(tourApiJsonMapper)));
        MockRestServiceServer lenientMockServer = MockRestServiceServer.bindTo(builder).build();
        TourApiClient lenientClient = new TourApiClient(builder.build(), new TourApiProperties(BASE_URL, "test-service-key"));

        lenientMockServer.expect(requestTo(containsString("/areaBasedList2")))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                        "body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}
                        """, MediaType.APPLICATION_JSON));

        AreaBasedPage page = lenientClient.fetchPlaces("11", "25", 1, 100);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }
}
