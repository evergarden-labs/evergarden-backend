package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import org.springframework.stereotype.Component;

/**
 * 일정 소유자 판별. 아카이브와 달리 공동 편집 개념이 없어 소유자 확인 하나뿐이라
 * 별도 클래스로 뺄 만큼 복잡하진 않지만, {@code TripService}·{@code TripPlaceService}
 * 둘 다 쓰게 될 거라 한곳에 모은다.
 */
@Component
public class TripAccessGuard {

    public void checkOwner(Trip trip, Long userId) {
        if (!trip.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }
}
