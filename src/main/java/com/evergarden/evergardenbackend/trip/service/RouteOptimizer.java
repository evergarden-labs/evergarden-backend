package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import java.util.ArrayList;
import java.util.List;

/**
 * 하루치 장소 목록을 이동 거리가 짧아지는 순서로 재배열한다(PLAN-09 · ADR-032).
 * 최근접 탐욕으로 초안을 만들고 2-opt로 다듬는다 — 좌표만 보는 결정적 계산이라
 * 같은 입력엔 항상 같은 결과가 나오고, 외부 API를 부르지 않는다.
 */
final class RouteOptimizer {

    private RouteOptimizer() {
    }

    /** 지금 순서의 첫 장소를 출발점으로 삼는다 — 그 외엔 입력 순서에 영향받지 않는다. */
    static List<TripPlace> optimize(List<TripPlace> places) {
        if (places.size() < 3) {
            return places; // 0~2개는 순서를 바꿔도 이동 거리가 똑같다
        }
        return twoOpt(nearestNeighbor(places));
    }

    private static List<TripPlace> nearestNeighbor(List<TripPlace> places) {
        List<TripPlace> remaining = new ArrayList<>(places);
        List<TripPlace> route = new ArrayList<>();

        TripPlace current = remaining.remove(0);
        route.add(current);
        while (!remaining.isEmpty()) {
            TripPlace nearest = remaining.get(0);
            long nearestDistance = GeoDistance.metersBetween(current, nearest);
            for (int i = 1; i < remaining.size(); i++) {
                TripPlace candidate = remaining.get(i);
                long distance = GeoDistance.metersBetween(current, candidate);
                // 거리가 같으면 tripPlaceId가 작은 쪽 — 그래야 결과가 결정적이다
                if (distance < nearestDistance
                        || (distance == nearestDistance && candidate.getId() < nearest.getId())) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
            remaining.remove(nearest);
            route.add(nearest);
            current = nearest;
        }
        return route;
    }

    /** 교차하는 두 구간을 뒤집었을 때 더 짧아지면 뒤집는다. 더 나아질 게 없을 때까지 반복한다. */
    private static List<TripPlace> twoOpt(List<TripPlace> route) {
        List<TripPlace> best = new ArrayList<>(route);
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < best.size() - 2; i++) {
                for (int j = i + 2; j < best.size(); j++) {
                    if (reversalShortens(best, i, j)) {
                        reverseSegment(best, i + 1, j);
                        improved = true;
                    }
                }
            }
        }
        return best;
    }

    private static boolean reversalShortens(List<TripPlace> route, int i, int j) {
        TripPlace a = route.get(i);
        TripPlace b = route.get(i + 1);
        TripPlace c = route.get(j);
        TripPlace d = j + 1 < route.size() ? route.get(j + 1) : null;

        long before = GeoDistance.metersBetween(a, b) + (d != null ? GeoDistance.metersBetween(c, d) : 0);
        long after = GeoDistance.metersBetween(a, c) + (d != null ? GeoDistance.metersBetween(b, d) : 0);
        return after < before;
    }

    private static void reverseSegment(List<TripPlace> route, int from, int to) {
        while (from < to) {
            TripPlace tmp = route.get(from);
            route.set(from, route.get(to));
            route.set(to, tmp);
            from++;
            to--;
        }
    }
}
