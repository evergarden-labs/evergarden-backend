package com.evergarden.evergardenbackend.admin.dto;

import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 명세의 {@code AdminUserDetail} 스키마(ADMIN-13) — {@code AdminUserSummary}에
 * 소셜 연결·게시물/댓글 수·제재 이력·탈퇴 시각을 더한 모양이다. OpenAPI의 {@code allOf}를
 * 자바에서 상속 대신 필드를 그대로 펼쳐 표현한다({@code ArchiveDetail}과 같은 방식).
 */
public record AdminUserDetail(
        Long userId,
        String nickname,
        UserStatus status,
        int validReportCount,
        LocalDateTime createdAt,
        List<String> socialProviders,
        int postCount,
        int commentCount,
        List<SanctionResponse> sanctions,
        LocalDateTime withdrawnAt) {

    public static AdminUserDetail of(User user, List<String> socialProviders, int postCount, int commentCount,
                                      List<SanctionResponse> sanctions) {
        return new AdminUserDetail(
                user.getId(), user.getNickname(), user.getStatus(), user.getValidReportCount(), user.getCreatedAt(),
                socialProviders, postCount, commentCount, sanctions, user.getWithdrawnAt());
    }
}
