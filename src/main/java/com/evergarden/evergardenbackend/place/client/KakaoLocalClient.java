package com.evergarden.evergardenbackend.place.client;

import com.evergarden.evergardenbackend.place.client.dto.Coordinate;
import com.evergarden.evergardenbackend.place.client.dto.KakaoAddressResponse;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로컬 "주소로 좌표 변환" 호출({@code docs/place-data-sync.md} 3절).
 *
 * <p>{@code Region.centerLat}·{@code centerLng}를 채우는 최초 1회 시딩에만 쓴다 —
 * TourAPI 법정동코드엔 좌표가 없어서다. 시/도만으로 검색하면 결과가 부정확할 수 있어,
 * 시군구는 호출하는 쪽에서 "시/도명 + 시군구명"을 합쳐 넘겨야 한다.
 */
@Component
@RequiredArgsConstructor
public class KakaoLocalClient {

    private final RestClient kakaoLocalRestClient;

    /** 주소·지역명으로 좌표를 찾는다. 결과가 없으면 빈 값. */
    public Optional<Coordinate> searchAddress(String query) {
        KakaoAddressResponse response = kakaoLocalRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search/address.json")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .body(KakaoAddressResponse.class);
        if (response == null || response.documents().isEmpty()) {
            return Optional.empty();
        }
        KakaoAddressResponse.Document first = response.documents().get(0);
        return Optional.of(new Coordinate(new BigDecimal(first.y()), new BigDecimal(first.x())));
    }
}
