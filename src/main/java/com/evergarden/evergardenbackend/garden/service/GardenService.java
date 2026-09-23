package com.evergarden.evergardenbackend.garden.service;

import com.evergarden.evergardenbackend.garden.dto.Garden;
import com.evergarden.evergardenbackend.garden.dto.UserGardenObjectResponse;
import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;
import com.evergarden.evergardenbackend.garden.repository.GardenObjectRepository;
import com.evergarden.evergardenbackend.garden.repository.UserGardenObjectRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정원 조회(GARDEN-01). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GardenService {

    private final UserGardenObjectRepository userGardenObjectRepository;
    private final GardenObjectRepository gardenObjectRepository;

    /**
     * 정원에 배치된 식물·오브젝트를 반환한다(GARDEN-01). 아직 해금하지 않은 건 담지 않는다
     * — {@code user_garden_objects}에 행 자체가 없어 조회 안 해도 자동으로 빠진다.
     * {@code totalCount}는 도감 전체 개수라 사용자와 무관하게 {@code GardenObject} 전체를 센다.
     * 해금한 순서로 반환한다 — 정렬을 안 걸면 응답 순서가 보장 안 된다.
     */
    public Garden getMyGarden(Long userId) {
        List<UserGardenObject> unlocked = userGardenObjectRepository.findByUser_IdOrderByUnlockedAtAsc(userId);
        List<UserGardenObjectResponse> objects = unlocked.stream().map(UserGardenObjectResponse::of).toList();
        long totalCount = gardenObjectRepository.count();
        return new Garden(objects, objects.size(), (int) totalCount);
    }
}
