package com.evergarden.evergardenbackend.place.service;

import com.evergarden.evergardenbackend.place.client.dto.AreaBasedItem;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관광지 한 건을 저장한다. {@link PlaceSyncService}와 별도 빈으로 뺀 이유는 트랜잭션
 * 경계를 항목 하나로 좁히기 위해서다 — 전체 시딩을 트랜잭션 하나로 묶었다가 실전에서
 * 사고가 났다: 14분 넘게 수백 번 API를 호출한 뒤 마지막에 항목 하나가 실패하니
 * 전부 롤백돼 DB엔 0건이 남고 오늘 호출 한도만 날렸다. 항목마다 독립된 트랜잭션이면
 * 하나 실패해도 그 앞까지 저장된 건 그대로 남는다.
 */
@Service
@RequiredArgsConstructor
public class PlaceUpsertService {

    private final PlaceRepository placeRepository;

    /** @return 새로 만들었으면 {@code true}, 기존 것을 갱신했으면 {@code false} */
    @Transactional
    public boolean upsertOne(AreaBasedItem item, Region region, BigDecimal lat, BigDecimal lng, LocalDateTime now) {
        Optional<Place> existing = placeRepository.findByContentId(item.contentid());
        if (existing.isPresent()) {
            Place place = existing.get();
            place.sync(item.title(), item.addr1(), item.tel(), lat, lng, region, item.firstimage2(),
                    place.getOverview(), place.getUseTime(), place.getRestDate(), now);
            return false;
        }

        Place place = Place.builder()
                .contentId(item.contentid())
                .contentTypeId(item.contenttypeid())
                .title(item.title())
                .addr(item.addr1())
                .tel(item.tel())
                .lat(lat)
                .lng(lng)
                .region(region)
                .thumbnailUrl(item.firstimage2())
                .syncedAt(now)
                .build();
        placeRepository.save(place);
        return true;
    }
}
