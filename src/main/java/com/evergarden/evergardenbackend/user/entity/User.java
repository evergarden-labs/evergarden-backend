package com.evergarden.evergardenbackend.user.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 회원. 소셜 로그인 전용이라 비밀번호 컬럼이 없다(ADR-010).
 *
 * <p>관리자는 이 테이블이 아니라 {@link Admin}에 따로 둔다.
 * 섞으면 "소셜 전용"이라는 정책이 테이블 안에서 깨진다(ADR-036).
 */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 한글·영문·숫자 1~20자, 중복 불가(ADR-053 · ADR-025) */
    @Column(nullable = false, length = 20, unique = true)
    private String nickname;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /** 유효 판정된 누적 신고. 1이면 경고, 3이면 차단(ADMIN-10) */
    @Column(name = "valid_report_count", nullable = false)
    private int validReportCount;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    /** 탈퇴 시각. 여기에 유예 기간을 더한 값이 복구 기한이다(ADR-054) */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Builder
    private User(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.status = UserStatus.ACTIVE;
        this.validReportCount = 0;
        this.onboardingCompleted = false;
    }

    /** 온보딩에서 닉네임과 프로필을 정하고 완료 처리한다(ONB-01). */
    public void completeOnboarding(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.onboardingCompleted = true;
    }

    /** 온보딩을 건너뛴다. 닉네임은 서버가 만들어 준 값을 그대로 쓴다(ONB-02 · ADR-050). */
    public void skipOnboarding() {
        this.onboardingCompleted = true;
    }

    /**
     * 프로필을 고친다(MY-02). {@code null}인 인자는 바꾸지 않는다.
     *
     * <p>온보딩 완료 처리는 하지 않는다. 초기 설정과 프로필 수정은 별개다(ADR-021).
     */
    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    /**
     * 탈퇴 처리한다(AUTH-04). 행을 지우지 않고 상태만 바꾼다(ADR-007).
     *
     * <p>게시물·댓글은 그대로 남고 작성자만 "탈퇴한 사용자"로 보인다(ADR-054).
     * 닉네임과 소셜 연결은 유예 기간이 지난 뒤 배치가 지운다.
     */
    public void withdraw(LocalDateTime now) {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = now;
    }

    /**
     * 유예 기간 안이면 계정을 되살린다(AUTH-07).
     *
     * <p>탈퇴할 때 종료된 공동 편집은 되돌리지 않는다. 그 사이 참여자들이
     * 이미 복제해 갔을 수 있어 되돌리면 상태가 엉킨다(ADR-054).
     */
    public void restore() {
        this.status = UserStatus.ACTIVE;
        this.withdrawnAt = null;
    }

    /** 복구 기한 안에 있는지. 기한은 설정값이라 밖에서 받는다(ADR-054). */
    public boolean isRestorableAt(LocalDateTime now, int graceDays) {
        return status == UserStatus.WITHDRAWN
                && withdrawnAt != null
                && now.isBefore(withdrawnAt.plusDays(graceDays));
    }

    /** 유효 신고가 하나 쌓인다. 임계값 판정은 제재 서비스가 한다(ADMIN-10). */
    public int increaseValidReportCount() {
        return ++this.validReportCount;
    }

    public void warn() {
        this.status = UserStatus.WARNED;
    }

    public void block() {
        this.status = UserStatus.BLOCKED;
    }

    /** 차단을 푼다(ADMIN-11). 경고 이력은 {@code validReportCount}에 그대로 남는다. */
    public void unblock() {
        this.status = UserStatus.ACTIVE;
    }

    /** 로그인과 대부분의 요청을 받을 수 있는 상태인지. */
    public boolean isActive() {
        return status == UserStatus.ACTIVE || status == UserStatus.WARNED;
    }
}
