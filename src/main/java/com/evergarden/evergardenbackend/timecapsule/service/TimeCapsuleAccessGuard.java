package com.evergarden.evergardenbackend.timecapsule.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsule;
import org.springframework.stereotype.Component;

/**
 * 타임캡슐 소유자 판별. 공동 편집 개념이 없어 소유자 확인 하나뿐이다
 * ({@code PostAccessGuard}·{@code TripAccessGuard}와 같은 패턴).
 */
@Component
public class TimeCapsuleAccessGuard {

    public void checkOwner(TimeCapsule capsule, Long userId) {
        if (!capsule.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }
}
