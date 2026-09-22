package com.evergarden.evergardenbackend.timecapsule.service;

import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleDetail;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleSummary;
import com.evergarden.evergardenbackend.timecapsule.dto.UnlockCondition;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsule;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleStatus;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@link TimeCapsule} 응답 변환. 목록은 {@link TimeCapsuleSummary}, 상세는 {@link TimeCapsuleDetail}. */
@Component
public class TimeCapsuleMapper {

    public TimeCapsuleSummary toSummary(TimeCapsule capsule, String thumbnailUrl) {
        return new TimeCapsuleSummary(
                capsule.getId(), capsule.getTitle(), capsule.getStatus(), capsule.getUnlockType(),
                thumbnailUrl, capsule.getCreatedAt(), capsule.getOpenedAt());
    }

    /**
     * @param thumbnailUrl 미리 계산해서 넘긴다. {@code OPENED}가 아니면 항상 {@code null}이어야 한다
     * @param media        미리 계산해서 넘긴다. {@code OPENED}가 아니면 항상 빈 목록이어야 한다
     */
    public TimeCapsuleDetail toDetail(TimeCapsule capsule, String thumbnailUrl, List<MediaResponse> media) {
        boolean opened = capsule.isOpened();
        return new TimeCapsuleDetail(
                capsule.getId(), capsule.getTitle(), capsule.getStatus(), capsule.getUnlockType(),
                thumbnailUrl, capsule.getCreatedAt(), capsule.getOpenedAt(),
                unlockCondition(capsule),
                opened ? capsule.getContent() : null,
                opened ? media : List.of());
    }

    private UnlockCondition unlockCondition(TimeCapsule capsule) {
        boolean satisfied = capsule.getStatus() != TimeCapsuleStatus.SEALED;
        Double lat = capsule.getUnlockLat() != null ? capsule.getUnlockLat().doubleValue() : null;
        Double lng = capsule.getUnlockLng() != null ? capsule.getUnlockLng().doubleValue() : null;
        return new UnlockCondition(capsule.getUnlockType(), satisfied,
                capsule.getUnlockDate(), lat, lng, capsule.getUnlockRadiusM(), capsule.getPlaceName());
    }
}
