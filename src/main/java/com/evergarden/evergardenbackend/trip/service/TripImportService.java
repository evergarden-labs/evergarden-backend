package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.trip.dto.ImportCourseRequest;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.entity.TripRegion;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRegionRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시물에 공유된 코스를 내 일정으로 복사한다(PLAN-12). 복사본의 {@code originTrip}에
 * 원본을 남기고(ADR-005), 이후 원본이 바뀌거나 지워져도 복사본은 영향받지 않는다 —
 * 참조가 아니라 값을 그대로 복제하기 때문이다.
 *
 * <p>{@code startDate}는 생략할 수 없다(ADR-059) — "날짜 없는 초안" 상태를 표현할 데이터
 * 구조가 없어서다. {@code ImportCourseRequest}의 {@code @NotNull}과 컨트롤러의
 * {@code @Valid}가 이미 걸러내므로 여기서는 다시 확인하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TripImportService {

    private final PostRepository postRepository;
    private final TripRepository tripRepository;
    private final TripRegionRepository tripRegionRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final UserRepository userRepository;
    private final TripService tripService;

    public TripDetail importCourse(Long userId, Long postId, ImportCourseRequest request) {
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        Trip original = post.getSharedTrip();
        if (original == null) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }

        LocalDate startDate = request.startDate();
        LocalDate endDate = startDate.plusDays(original.durationDays() - 1);
        String title = request.title() != null ? request.title() : original.getTitle();

        Trip copy = Trip.builder()
                .owner(userRepository.getReferenceById(userId))
                .title(title)
                .startDate(startDate)
                .endDate(endDate)
                .originTrip(original)
                .build();
        tripRepository.save(copy);

        copyRegions(original, copy);
        copyPlaces(original, copy);

        return tripService.toDetail(copy);
    }

    private void copyRegions(Trip original, Trip copy) {
        List<TripRegion> regions = tripRegionRepository.findByTrip(original).stream()
                .map(tripRegion -> new TripRegion(copy, tripRegion.getRegion()))
                .toList();
        tripRegionRepository.saveAll(regions);
    }

    private void copyPlaces(Trip original, Trip copy) {
        List<TripPlace> places = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(original).stream()
                .map(tripPlace -> TripPlace.builder()
                        .trip(copy)
                        .place(tripPlace.getPlace())
                        .dayNumber(tripPlace.getDayNumber())
                        .sortOrder(tripPlace.getSortOrder())
                        .memo(tripPlace.getMemo())
                        .build())
                .toList();
        tripPlaceRepository.saveAll(places);
    }
}
