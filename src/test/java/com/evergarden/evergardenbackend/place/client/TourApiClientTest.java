package com.evergarden.evergardenbackend.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.evergarden.evergardenbackend.place.client.dto.AreaBasedPage;
import com.evergarden.evergardenbackend.place.client.dto.RegionCode;
import com.evergarden.evergardenbackend.place.config.TourApiProperties;
import java.util.List;
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
