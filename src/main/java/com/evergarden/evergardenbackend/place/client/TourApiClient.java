package com.evergarden.evergardenbackend.place.client;

import com.evergarden.evergardenbackend.place.client.dto.AreaBasedItem;
import com.evergarden.evergardenbackend.place.client.dto.AreaBasedPage;
import com.evergarden.evergardenbackend.place.client.dto.LdongDistrictItem;
import com.evergarden.evergardenbackend.place.client.dto.RegionCode;
import com.evergarden.evergardenbackend.place.client.dto.TourApiEnvelope;
import com.evergarden.evergardenbackend.place.config.TourApiProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 한국관광공사 국문 관광정보 서비스(KorService2) 호출({@code docs/place-data-sync.md} 2절).
 *
 * <p>법정동코드 조회({@code ldongCode2})는 시/도 전체 17개, 시군구도 시/도당
 * 많아야 수십 개라 페이지네이션 없이 한 번에 받는다. 지역기반 관광정보 조회
 * ({@code areaBasedList2})는 건수가 많아 호출하는 쪽이 페이지를 넘겨가며 불러야 한다.
 *
 * <p>{@code serviceKey}는 {@link URLEncoder}로 직접 인코딩해 완성된 {@link URI}로 호출한다.
 * 서비스키에 흔히 섞이는 {@code +}를 스프링의 {@code UriBuilder}에 넘기면 RFC 3986상
 * 안전한 문자라 그대로 두는데, TourAPI 서버는 폼 인코딩 관례대로 {@code +}를 공백으로
 * 해석해서 키가 깨진다 — 실전에서 "등록되지 않은 서비스키" 오류로 확인된 문제다.
 */
@Component
@RequiredArgsConstructor
public class TourApiClient {

    private static final String LDONG_CODE_PATH = "/ldongCode2";
    private static final String AREA_BASED_LIST_PATH = "/areaBasedList2";
    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "evergarden";
    private static final int MAX_ROWS = 100;

    private final RestClient tourApiRestClient;
    private final TourApiProperties tourApiProperties;

    /** 법정동 시/도 전체 목록(17개). */
    public List<RegionCode> fetchProvinces() {
        TourApiEnvelope<RegionCode> envelope = tourApiRestClient.get()
                .uri(ldongCodeUri(""))
                .retrieve()
                .body(new ParameterizedTypeReference<TourApiEnvelope<RegionCode>>() {
                });
        return envelope == null ? List.of() : envelope.items();
    }

    /** 주어진 시/도({@code provinceCode})에 속한 시군구 목록. */
    public List<RegionCode> fetchDistricts(String provinceCode) {
        TourApiEnvelope<LdongDistrictItem> envelope = tourApiRestClient.get()
                .uri(ldongCodeUri("&lDongRegnCd=" + provinceCode + "&lDongListYn=Y"))
                .retrieve()
                .body(new ParameterizedTypeReference<TourApiEnvelope<LdongDistrictItem>>() {
                });
        if (envelope == null) {
            return List.of();
        }
        return envelope.items().stream()
                .map(item -> new RegionCode(item.lDongSignguCd(), item.lDongSignguNm()))
                .toList();
    }

    private URI ldongCodeUri(String extraParams) {
        String query = commonQuery(MAX_ROWS) + extraParams;
        return URI.create(tourApiProperties.baseUrl() + LDONG_CODE_PATH + "?" + query);
    }

    /**
     * 지역기반 관광정보 조회({@code areaBasedList2}). 시군구까지 안 좁히고 시/도만
     * 넘긴다 — 그 안의 모든 시군구 결과가 한 번에 나오고, 응답 항목마다
     * {@code lDongSignguCd}가 있어 나중에 어느 시군구인지 알 수 있다.
     */
    public AreaBasedPage fetchPlaces(String provinceCode, String contentTypeId, int pageNo, int numOfRows) {
        String query = commonQuery(numOfRows)
                + "&pageNo=" + pageNo
                + "&lDongRegnCd=" + provinceCode
                + "&contentTypeId=" + contentTypeId
                + "&arrange=C";
        URI uri = URI.create(tourApiProperties.baseUrl() + AREA_BASED_LIST_PATH + "?" + query);

        TourApiEnvelope<AreaBasedItem> envelope = tourApiRestClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<TourApiEnvelope<AreaBasedItem>>() {
                });
        return envelope == null
                ? new AreaBasedPage(List.of(), 0)
                : new AreaBasedPage(envelope.items(), envelope.totalCount());
    }

    private String commonQuery(int numOfRows) {
        String encodedKey = URLEncoder.encode(tourApiProperties.serviceKey(), StandardCharsets.UTF_8);
        return "serviceKey=" + encodedKey
                + "&MobileOS=" + MOBILE_OS
                + "&MobileApp=" + MOBILE_APP
                + "&_type=json"
                + "&numOfRows=" + numOfRows;
    }
}
