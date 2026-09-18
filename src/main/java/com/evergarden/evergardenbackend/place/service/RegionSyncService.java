package com.evergarden.evergardenbackend.place.service;

import com.evergarden.evergardenbackend.place.client.KakaoLocalClient;
import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.Coordinate;
import com.evergarden.evergardenbackend.place.client.dto.RegionCode;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 법정동 시/도·시군구를 {@code Region}에 시딩한다({@code docs/place-data-sync.md}).
 *
 * <p>최초 1회(또는 드물게 재실행)만 도는 작업이라 스케줄러에 걸지 않는다 — 매일 도는 건
 * {@code Place} 동기화 쪽이다. TourAPI로 코드·이름을, 카카오로 좌표를 받아 합친다.
 *
 * <p>시/도 좌표를 못 찾으면 그 산하 시군구는 아예 조회하지 않는다 — 시군구의
 * {@code parent}가 그 시/도 {@link Region}이라, 시/도 자체가 저장 안 됐으면 시군구도
 * 저장할 방법이 없다.
 *
 * <p>TourAPI의 시군구 코드({@code lDongSignguCd})는 그 시/도 안에서만 유일하다 — 서울
 * 종로구도 "110", 전북 전주시도 "110"이라 전국 단위로는 겹친다. 그래서 시군구의
 * {@code Region.code}는 시/도 코드를 앞에 붙여("11"+"110") 전국에서 유일하게 만든다.
 * 처음엔 이걸 놓쳐서 나중에 처리된 지역이 먼저 저장된 걸 덮어쓰는 사고가 실제로 났었다.
 *
 * <p>~270개 지역을 외부 API 호출과 함께 트랜잭션 하나로 처리한다. 최초 시딩 한 번뿐인
 * 배치라 지금은 받아들이지만, 반복 실행하는 배치가 되면 쪼개야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RegionSyncService {

    private final TourApiClient tourApiClient;
    private final KakaoLocalClient kakaoLocalClient;
    private final RegionRepository regionRepository;

    public RegionSyncResult syncAll() {
        LocalDateTime now = LocalDateTime.now();
        List<String> failedQueries = new ArrayList<>();
        int provinceCount = 0;
        int districtCount = 0;

        for (RegionCode province : tourApiClient.fetchProvinces()) {
            Optional<Coordinate> provinceCoordinate = kakaoLocalClient.searchAddress(province.name());
            if (provinceCoordinate.isEmpty()) {
                failedQueries.add(province.name());
                continue;
            }
            Region provinceRegion = upsert(province.code(), null, RegionLevel.SIDO,
                    province.name(), provinceCoordinate.get(), now);
            provinceCount++;

            for (RegionCode district : tourApiClient.fetchDistricts(province.code())) {
                String query = province.name() + " " + district.name();
                Optional<Coordinate> districtCoordinate = kakaoLocalClient.searchAddress(query);
                if (districtCoordinate.isEmpty()) {
                    failedQueries.add(query);
                    continue;
                }
                upsert(province.code() + district.code(), provinceRegion, RegionLevel.SIGUNGU,
                        district.name(), districtCoordinate.get(), now);
                districtCount++;
            }
        }
        return new RegionSyncResult(provinceCount, districtCount, failedQueries);
    }

    private Region upsert(String code, Region parent, RegionLevel level, String name,
                          Coordinate coordinate, LocalDateTime now) {
        return regionRepository.findById(code)
                .map(existing -> {
                    existing.sync(name, coordinate.lat(), coordinate.lng(), now);
                    return existing;
                })
                .orElseGet(() -> regionRepository.save(Region.builder()
                        .code(code)
                        .parent(parent)
                        .level(level)
                        .name(name)
                        .centerLat(coordinate.lat())
                        .centerLng(coordinate.lng())
                        .syncedAt(now)
                        .build()));
    }
}
