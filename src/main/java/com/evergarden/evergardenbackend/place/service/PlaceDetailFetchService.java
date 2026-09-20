package com.evergarden.evergardenbackend.place.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.DetailCommonItem;
import com.evergarden.evergardenbackend.place.client.dto.DetailImageItem;
import com.evergarden.evergardenbackend.place.client.dto.DetailIntroItem;
import com.evergarden.evergardenbackend.place.entity.Place;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * {@code getPlace}(PLAN-07)의 라이브 상세조회. {@code overview}(공통정보조회)·
 * {@code useTime}/{@code restDate}(소개정보조회)·{@code imageUrls}(관광사진정보조회)를
 * 한 번만 받아 {@code places}에 영구 저장한다(ADR-061) — "짧은 TTL 캐시"가 아니라
 * DB 자체가 캐시다. 콘텐츠랩 운영시간이 자주 바뀌는 값이 아니라 매번 다시 물을
 * 필요가 없고, 이미 있는 {@code places} 행을 그대로 쓰니 별도 캐시 저장소(Redis)도
 * 필요 없다.
 */
@Service
@RequiredArgsConstructor
public class PlaceDetailFetchService {

    private final TourApiClient tourApiClient;

    /** {@link Place#hasDetail()}가 이미 {@code true}면 아무것도 안 한다. */
    @Transactional
    public void ensureDetailFetched(Place place) {
        if (place.hasDetail()) {
            return;
        }

        try {
            String overview = tourApiClient.fetchDetailCommon(place.getContentId())
                    .map(DetailCommonItem::overview)
                    .orElse(null);

            DetailIntroItem intro = tourApiClient
                    .fetchDetailIntro(place.getContentId(), place.getContentTypeId())
                    .orElse(null);
            String useTime = intro == null ? null : intro.resolveUseTime();
            String restDate = intro == null ? null : intro.resolveRestDate();

            List<String> imageUrls = tourApiClient.fetchDetailImages(place.getContentId()).stream()
                    .map(DetailImageItem::originimgurl)
                    .filter(url -> url != null && !url.isBlank())
                    .toList();

            place.syncDetail(overview, useTime, restDate, imageUrls, LocalDateTime.now());
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.TOUR_API_UNAVAILABLE);
        }
    }
}
