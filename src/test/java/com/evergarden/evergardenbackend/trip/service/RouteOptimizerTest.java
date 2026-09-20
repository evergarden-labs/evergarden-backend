package com.evergarden.evergardenbackend.trip.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 최근접 탐욕 + 2-opt(PLAN-09 · ADR-032)가 실제로 얼마나 최적에 가까운지, 완전탐색
 * (모든 순열)과 비교해 확인한다. 출발점(입력의 첫 장소)은 고정하고 나머지만 순열을
 * 돌린다 — {@link RouteOptimizer}의 계약과 같은 조건이라야 공정한 비교다.
 *
 * <p>2-opt는 지역 탐색이라 전역 최적을 못 찾을 때가 있다 — 실제로 이 테스트의 고정 시드로
 * 120세트를 돌려보면 평균은 완전탐색 대비 0.8%밖에 안 차이 나지만, 가장 나쁜 경우는
 * 14.6%까지 벌어졌다(4~7개짜리 작은 경로일수록 2-opt가 시도할 수 있는 수가 적어서다).
 * 그래서 20% 이내를 통과 기준으로 잡았다 — 실측 최악값에 여유를 둔 값이지, 임의로 정한
 * 숫자가 아니다. 무작위 시드를 고정해 매번 같은 입력으로 재현 가능하다.
 */
class RouteOptimizerTest {

    private static final Trip TRIP = trip();

    private static Trip trip() {
        User owner = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(owner, "id", 1L);
        return Trip.builder().owner(owner).title("테스트 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 1)).build();
    }

    private TripPlace randomPlace(long id, Random random) {
        // 서울 시내 규모(대략 위경도 0.2도 범위)에 흩어진 임의의 점.
        double lat = 37.45 + random.nextDouble() * 0.2;
        double lng = 126.9 + random.nextDouble() * 0.2;
        Place place = Place.builder().contentId("c" + id).contentTypeId("12").title("장소" + id)
                .lat(BigDecimal.valueOf(lat)).lng(BigDecimal.valueOf(lng)).build();
        ReflectionTestUtils.setField(place, "id", id);
        TripPlace tripPlace = TripPlace.builder().trip(TRIP).place(place)
                .dayNumber((short) 1).sortOrder((short) (id + 1)).build();
        ReflectionTestUtils.setField(tripPlace, "id", id);
        return tripPlace;
    }

    @Test
    @DisplayName("4~7개 장소 무작위 배치 30세트에서, 완전탐색 최적 대비 20% 이내로 근접한다")
    void 완전탐색과_비교() {
        Random random = new Random(42); // 재현 가능하도록 고정 시드

        for (int size = 4; size <= 7; size++) {
            for (int trial = 0; trial < 30; trial++) {
                List<TripPlace> places = new ArrayList<>();
                for (long id = 0; id < size; id++) {
                    places.add(randomPlace(id, random));
                }

                List<TripPlace> optimized = RouteOptimizer.optimize(places);
                long optimizedDistance = totalDistance(optimized);
                long bruteForceOptimal = bruteForceOptimalDistance(places);

                assertThat(optimizedDistance)
                        .as("size=%d trial=%d 최적화 결과가 완전탐색보다 짧을 수는 없다", size, trial)
                        .isGreaterThanOrEqualTo(bruteForceOptimal);
                assertThat((double) optimizedDistance)
                        .as("size=%d trial=%d 완전탐색(%d) 대비 20%% 이내여야 한다 (결과 %d)",
                                size, trial, bruteForceOptimal, optimizedDistance)
                        .isLessThanOrEqualTo(bruteForceOptimal * 1.20);
            }
        }
    }

    @Test
    @DisplayName("장소가 2개 이하면 순서를 바꾸지 않고 그대로 돌려준다")
    void 두개이하는_그대로() {
        Random random = new Random(1);
        List<TripPlace> one = List.of(randomPlace(0, random));
        List<TripPlace> two = List.of(randomPlace(0, random), randomPlace(1, random));

        assertThat(RouteOptimizer.optimize(one)).isEqualTo(one);
        assertThat(RouteOptimizer.optimize(two)).isEqualTo(two);
    }

    /** 첫 장소를 출발점으로 고정하고 나머지의 모든 순열을 시도한다. */
    private long bruteForceOptimalDistance(List<TripPlace> places) {
        TripPlace start = places.get(0);
        List<TripPlace> rest = new ArrayList<>(places.subList(1, places.size()));
        long[] best = {Long.MAX_VALUE};
        permute(rest, 0, start, 0L, best);
        return best[0];
    }

    private void permute(List<TripPlace> rest, int index, TripPlace lastFixed, long distanceSoFar, long[] best) {
        if (index == rest.size()) {
            best[0] = Math.min(best[0], distanceSoFar);
            return;
        }
        for (int i = index; i < rest.size(); i++) {
            swap(rest, index, i);
            long added = GeoDistance.metersBetween(index == 0 ? lastFixed : rest.get(index - 1), rest.get(index));
            permute(rest, index + 1, lastFixed, distanceSoFar + added, best);
            swap(rest, index, i);
        }
    }

    private void swap(List<TripPlace> list, int i, int j) {
        TripPlace tmp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, tmp);
    }

    private long totalDistance(List<TripPlace> route) {
        long total = 0;
        for (int i = 1; i < route.size(); i++) {
            total += GeoDistance.metersBetween(route.get(i - 1), route.get(i));
        }
        return total;
    }
}
