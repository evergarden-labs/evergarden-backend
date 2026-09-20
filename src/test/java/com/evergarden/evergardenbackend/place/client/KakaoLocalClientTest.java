package com.evergarden.evergardenbackend.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.evergarden.evergardenbackend.place.client.dto.Coordinate;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoLocalClientTest {

    private static final String BASE_URL = "https://dapi.kakao.com/v2/local";

    private MockRestServiceServer mockServer;
    private KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "KakaoAK test-rest-api-key");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoLocalClient(builder.build());
    }

    @Test
    @DisplayName("주소로 좌표를 찾으면 x=경도, y=위도로 매핑한다")
    void 주소로_좌표찾기() {
        mockServer.expect(requestTo(containsString("/search/address.json")))
                .andExpect(header("Authorization", "KakaoAK test-rest-api-key"))
                .andRespond(withSuccess("""
                        {"documents":[{"x":"126.9783882","y":"37.5666103"}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<Coordinate> coordinate = client.searchAddress("서울특별시 종로구");

        assertThat(coordinate).contains(new Coordinate(new BigDecimal("37.5666103"), new BigDecimal("126.9783882")));
    }

    @Test
    @DisplayName("결과가 없으면 빈 값을 돌려준다")
    void 결과없음() {
        mockServer.expect(requestTo(containsString("/search/address.json")))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        Optional<Coordinate> coordinate = client.searchAddress("존재하지않는주소일까");

        assertThat(coordinate).isEmpty();
    }
}
