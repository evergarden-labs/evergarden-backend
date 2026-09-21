package com.evergarden.evergardenbackend.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code POST /posts}(COMM-04)의 요청 본문.
 *
 * @param tripId      공유할 코스. {@code archiveId}와 최소 하나는 있어야 한다
 * @param archiveId   공유할 아카이브. {@code tripId}와 최소 하나는 있어야 한다
 * @param regionCodes 코스 없이 아카이브만 공유할 때 필수다. 코스를 공유하면 일정의
 *                    여행지와 담긴 장소들의 지역을 서버가 자동으로 합친다(ADR-003)
 */
public record PostCreateRequest(
        @NotBlank @Size(min = 1, max = 2000) String content,
        Long tripId,
        Long archiveId,
        List<String> regionCodes) {
}
