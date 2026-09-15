package com.evergarden.evergardenbackend.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.evergarden.evergardenbackend.place.client.dto.RegionCode;
import com.evergarden.evergardenbackend.place.config.TourApiProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
}
