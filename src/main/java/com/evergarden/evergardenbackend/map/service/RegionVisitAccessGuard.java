package com.evergarden.evergardenbackend.map.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.map.entity.RegionVisit;
import org.springframework.stereotype.Component;

/** 방문 기록 소유자 판별(GARDEN-02). 공동 편집 개념이 없어 소유자 확인 하나뿐이다. */
@Component
public class RegionVisitAccessGuard {

    public void checkOwner(RegionVisit visit, Long userId) {
        if (!visit.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }
}
