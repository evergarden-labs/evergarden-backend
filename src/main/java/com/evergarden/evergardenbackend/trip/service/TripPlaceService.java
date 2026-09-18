package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceCreateRequest;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceOrderRequest;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceResponse;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceUpdateRequest;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정에 담긴 장소 관리(PLAN-02·04·08).
 *
 * <p>{@code (trip_id, day_number, sort_order)}가 DB 유니크 제약이라, 자리를 밀거나
 * 당길 때 순서를 잘못 처리하면 트랜잭션 중간에 유니크 위반이 난다 — Postgres는 지연
 * 제약이 아니라서 각 UPDATE 문이 실행되는 즉시 검사한다. 그래서:
 * <ul>
 *   <li>끼워넣기(자리를 만들 때)는 <b>높은 순서부터</b> 하나씩 뒤로 미룬다</li>
 *   <li>빼기(빈자리를 당길 때)는 <b>낮은 순서부터</b> 하나씩 앞으로 당긴다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TripPlaceService {

    /**
     * 자리를 비켜 두는 임시 순서값의 시작점. {@code sort_order}는 DB에 {@code CHECK (>= 1)}이
     * 걸려 있어 음수를 쓸 수 없다 — 한 날짜에 이 값을 넘는 장소가 쌓일 일은 없다고 보고,
     * 실제 값과 절대 안 겹치는 충분히 큰 자리를 임시로 쓴다.
     */
    private static final short SCRATCH_ORDER_BASE = 30000;

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final PlaceRepository placeRepository;
    private final TripAccessGuard accessGuard;
    private final TripMapper tripMapper;
    private final TripService tripService;

    public TripPlaceResponse addPlace(Long userId, Long tripId, TripPlaceCreateRequest request) {
        Trip trip = findTrip(tripId);
        accessGuard.checkOwner(trip, userId);
        short dayNumber = validateDayNumber(trip, request.dayNumber());

        Place place = placeRepository.findById(request.placeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        short sortOrder;
        if (request.sortOrder() != null) {
            sortOrder = request.sortOrder();
            makeRoom(trip, dayNumber, sortOrder);
        } else {
            sortOrder = (short) (tripPlaceRepository.countByTripAndDayNumber(trip, dayNumber) + 1);
        }

        TripPlace tripPlace = TripPlace.builder()
                .trip(trip)
                .place(place)
                .dayNumber(dayNumber)
                .sortOrder(sortOrder)
                .memo(request.memo())
                .build();
        tripPlaceRepository.save(tripPlace);
        return toResponse(tripPlace);
    }

    public TripPlaceResponse updatePlace(Long userId, Long tripId, Long tripPlaceId, TripPlaceUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Trip trip = findTrip(tripId);
        accessGuard.checkOwner(trip, userId);
        TripPlace tripPlace = findTripPlace(trip, tripPlaceId);

        if (request.dayNumber() != null || request.sortOrder() != null) {
            short oldDay = tripPlace.getDayNumber();
            short oldOrder = tripPlace.getSortOrder();
            short newDay = request.dayNumber() != null ? validateDayNumber(trip, request.dayNumber()) : oldDay;

            // 옛 자리를 자기 자신이 계속 차지한 채로 closeGap을 부르면 그 자리를 채우려는
            // 뒤쪽 항목과 자기 자신이 부딪힌다 — 먼저 임시 자리로 비켜 둔다.
            tripPlace.relocate(oldDay, SCRATCH_ORDER_BASE);
            tripPlaceRepository.flush();
            closeGap(trip, oldDay, oldOrder);

            short newOrder;
            if (request.sortOrder() != null) {
                newOrder = request.sortOrder();
            } else {
                long count = tripPlaceRepository.countByTripAndDayNumber(trip, newDay);
                // 같은 날짜로 옮기는 경우, 위에서 비켜 둔 자기 자신도 그 날짜에 잡혀 하나 더 세인다.
                newOrder = (short) (count - (newDay == oldDay ? 1 : 0) + 1);
            }
            makeRoom(trip, newDay, newOrder);
            tripPlace.relocate(newDay, newOrder);
            tripPlaceRepository.flush();
        }
        if (request.memo() != null) {
            tripPlace.updateMemo(request.memo());
        }
        return toResponse(tripPlace);
    }

    public void removePlace(Long userId, Long tripId, Long tripPlaceId) {
        Trip trip = findTrip(tripId);
        accessGuard.checkOwner(trip, userId);
        TripPlace tripPlace = findTripPlace(trip, tripPlaceId);

        short dayNumber = tripPlace.getDayNumber();
        short order = tripPlace.getSortOrder();
        tripPlaceRepository.delete(tripPlace);
        closeGap(trip, dayNumber, order);
    }

    /**
     * 일정에 담긴 모든 장소의 일자·순서를 통째로 다시 매긴다(PLAN-02·04). 부분 전송은 거부한다.
     *
     * <p>여러 건이 한꺼번에 자리를 맞바꿀 수 있어 "밀기·당기기"로는 안전하지 않다 —
     * 전부를 실제 값과 안 겹치는 임시 순서로 옮겨 비워둔(그래서 서로 절대 안 겹치는) 다음,
     * 요청받은 자리로 채운다.
     */
    public TripDetail replaceOrder(Long userId, Long tripId, TripPlaceOrderRequest request) {
        Trip trip = findTrip(tripId);
        accessGuard.checkOwner(trip, userId);

        List<TripPlace> current = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        if (current.size() != request.items().size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Map<Long, TripPlace> byId = current.stream().collect(Collectors.toMap(TripPlace::getId, tp -> tp));

        for (TripPlaceOrderRequest.Item item : request.items()) {
            if (!byId.containsKey(item.tripPlaceId())) {
                throw new BusinessException(ErrorCode.TRIP_PLACE_NOT_FOUND);
            }
            validateDayNumber(trip, item.dayNumber());
        }

        short temp = SCRATCH_ORDER_BASE;
        for (TripPlace tripPlace : current) {
            tripPlace.relocate(tripPlace.getDayNumber(), temp++);
        }
        // 임시 자리로 비워 둔 상태를 DB에 실제로 반영해야, 최종 자리로 채울 때 기존 값과 안 부딪힌다.
        tripPlaceRepository.flush();
        for (TripPlaceOrderRequest.Item item : request.items()) {
            byId.get(item.tripPlaceId()).relocate(item.dayNumber(), item.sortOrder());
        }
        tripPlaceRepository.flush();

        return tripService.toDetail(trip);
    }

    /**
     * dayNumber 이상인 자리를 한 칸씩 뒤로 밀어 dayNumber 자리를 비운다.
     *
     * <p>JPA는 변경된 필드를 바로 UPDATE로 내보내지 않고 flush 시점까지 모아 둔다 —
     * 그래서 매 건마다 {@link TripPlaceRepository#flush()}로 즉시 내보내지 않으면,
     * 뒤이어 이 메서드를 부른 쪽이 새로 끼워 넣는 행의 INSERT(식별자 생성 전략상
     * 미룰 수 없어 그 자리에서 바로 나간다)가 아직 DB에 반영되지 않은 밀기보다
     * 먼저 실행돼 유니크 제약과 부딪힌다.
     */
    private void makeRoom(Trip trip, short dayNumber, short fromOrder) {
        for (TripPlace tp : tripPlaceRepository.findByTripAndDayNumberOrderBySortOrderDesc(trip, dayNumber)) {
            if (tp.getSortOrder() >= fromOrder) {
                tp.relocate(dayNumber, (short) (tp.getSortOrder() + 1));
                tripPlaceRepository.flush();
            }
        }
    }

    /** order보다 뒤에 있던 자리들을 한 칸씩 당겨 빈자리를 채운다. 이유는 {@link #makeRoom}과 같다. */
    private void closeGap(Trip trip, short dayNumber, short order) {
        for (TripPlace tp : tripPlaceRepository.findByTripAndDayNumberOrderBySortOrderAsc(trip, dayNumber)) {
            if (tp.getSortOrder() > order) {
                tp.relocate(dayNumber, (short) (tp.getSortOrder() - 1));
                tripPlaceRepository.flush();
            }
        }
    }

    /** dayNumber가 일정 기간(1 ~ durationDays) 안에 있는지 확인한다. */
    private short validateDayNumber(Trip trip, short dayNumber) {
        if (dayNumber < 1 || dayNumber > trip.durationDays()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return dayNumber;
    }

    private TripPlace findTripPlace(Trip trip, Long tripPlaceId) {
        return tripPlaceRepository.findByIdAndTrip(tripPlaceId, trip)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_PLACE_NOT_FOUND));
    }

    private Trip findTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
    }

    private TripPlaceResponse toResponse(TripPlace tripPlace) {
        return tripMapper.toPlaceResponse(tripPlace);
    }
}
