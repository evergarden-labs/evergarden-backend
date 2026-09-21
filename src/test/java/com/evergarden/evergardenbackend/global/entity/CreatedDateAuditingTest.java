package com.evergarden.evergardenbackend.global.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.auth.entity.SocialAccount;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.auth.repository.SocialAccountRepository;
import com.evergarden.evergardenbackend.map.entity.RegionVisit;
import com.evergarden.evergardenbackend.map.repository.RegionVisitRepository;
import com.evergarden.evergardenbackend.notification.entity.Notification;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.repository.NotificationRepository;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.report.entity.Sanction;
import com.evergarden.evergardenbackend.report.entity.SanctionType;
import com.evergarden.evergardenbackend.report.repository.SanctionRepository;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code BaseTimeEntity}를 상속하지 않고 {@code @CreatedDate}만 직접 둔 연결
 * 테이블·이력 엔티티들이 실제로 {@code createdAt}을 채우는지 확인한다.
 *
 * <p>{@code PostLike}에서 {@code @EntityListeners(AuditingEntityListener.class)}가
 * 빠지면 항상 {@code null}로 저장을 시도해 {@code NOT NULL} 제약에 매번 걸리는
 * 걸 실전에서 확인했다 — 같은 모양(연결 테이블, {@code createdAt}만 직접 선언)인
 * {@code SocialAccount}·{@code Notification}·{@code RegionVisit}·{@code Sanction}도
 * 같은 실수를 하기 쉬워서, 서비스 계층이 아직 없어도 리포지토리 단에서 미리 잡는다.
 */
@Transactional
class CreatedDateAuditingTest extends IntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired RegionVisitRepository regionVisitRepository;
    @Autowired SanctionRepository sanctionRepository;

    private User user() {
        return userRepository.save(User.builder().nickname("테스터").build());
    }

    @Test
    @DisplayName("SocialAccount는 저장 시 createdAt이 채워진다")
    void 소셜계정_생성시각_채워짐() {
        SocialAccount saved = socialAccountRepository.saveAndFlush(SocialAccount.builder()
                .user(user()).provider(SocialProvider.KAKAO).providerUserId("kakao-1").build());

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Notification은 저장 시 createdAt이 채워진다")
    void 알림_생성시각_채워짐() {
        Notification saved = notificationRepository.saveAndFlush(Notification.builder()
                .receiver(user()).type(NotificationType.CAPSULE_UNLOCK).title("타임캡슐 해제").build());

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("RegionVisit은 저장 시 createdAt이 채워진다")
    void 지역방문_생성시각_채워짐() {
        Region region = regionRepository.save(Region.builder().code("11").level(RegionLevel.SIDO)
                .name("서울특별시").centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO)
                .syncedAt(LocalDateTime.now()).build());

        RegionVisit saved = regionVisitRepository.saveAndFlush(RegionVisit.builder()
                .user(user()).region(region).lat(BigDecimal.valueOf(37.5665)).lng(BigDecimal.valueOf(126.9780))
                .verifiedAt(LocalDateTime.now()).build());

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Sanction은 저장 시 createdAt이 채워진다")
    void 제재_생성시각_채워짐() {
        Sanction saved = sanctionRepository.saveAndFlush(
                Sanction.automatic(user(), SanctionType.WARNING, "유효 신고 누적"));

        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
