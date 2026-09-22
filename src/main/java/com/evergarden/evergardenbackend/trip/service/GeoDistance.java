package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import java.math.BigDecimal;

/**
 * 좌표 두 점 사이의 직선(대권) 거리(ADR-043). 하버사인 공식을 쓴다 — 실제 도로를
 * 따라간 거리가 아니라 지구 표면을 따라 잰 최단 거리라, 실제보다 짧게 나온다.
 *
 * <p>{@link TripRouteService}(동선 조회)와 자동 배치가 같은 계산을 써야 두 화면의
 * 숫자가 일치한다(ADR-043) — 그래서 좌표 두 쌍짜리 계산은 패키지 밖에서도(타임캡슐
 * 위치 해제 판정, TC-04) 재사용하도록 공개해 두고, 트립 전용 타입을 받는 오버로드만
 * 패키지 안에 둔다.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private GeoDistance() {
    }

    public static long metersBetween(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLambda = Math.toRadians(lng2.subtract(lng1).doubleValue());

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_METERS * c);
    }

    /** 일정에 담긴 두 장소 사이의 거리. */
    static long metersBetween(TripPlace a, TripPlace b) {
        return metersBetween(a.getPlace(), b.getPlace());
    }

    /** 관광지 두 곳 사이의 거리. */
    static long metersBetween(Place a, Place b) {
        return metersBetween(a.getLat(), a.getLng(), b.getLat(), b.getLng());
    }
}
