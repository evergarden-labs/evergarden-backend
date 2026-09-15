package com.evergarden.evergardenbackend.archive.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 순수 도메인 로직만 — 영속성은 Testcontainers 통합 테스트가 따로 본다. */
class ArchiveTest {

    private static final Long OWNER_ID = 1L;

    private User owner() {
        User owner = User.builder().nickname("소유자").build();
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        return owner;
    }

    private Archive archive() {
        return Archive.builder().owner(owner()).title("제주 여행").theme(ArchiveTheme.POLAROID).build();
    }

    @Test
    @DisplayName("생성 직후엔 공동편집 상태가 NONE이다")
    void 생성직후_NONE() {
        assertThat(archive().getCollaborationStatus()).isEqualTo(CollaborationStatus.NONE);
    }

    @Test
    @DisplayName("update — 보낸 필드만 바뀌고 나머지는 그대로다")
    void update_보낸_필드만() {
        Archive archive = archive();

        archive.update("이름만 변경", null, null);

        assertThat(archive.getTitle()).isEqualTo("이름만 변경");
        assertThat(archive.getTheme()).isEqualTo(ArchiveTheme.POLAROID);
        assertThat(archive.getPrimaryColor()).isNull();
    }

    @Test
    @DisplayName("update — 전부 null이면 아무것도 안 바뀐다")
    void update_전부_null() {
        Archive archive = archive();

        archive.update(null, null, null);

        assertThat(archive.getTitle()).isEqualTo("제주 여행");
        assertThat(archive.getTheme()).isEqualTo(ArchiveTheme.POLAROID);
    }

    @Test
    @DisplayName("linkTrip — 일정을 잇거나 null로 끊는다")
    void linkTrip_연결과_해제() {
        Archive archive = archive();
        Trip trip = mock(Trip.class);

        archive.linkTrip(trip);
        assertThat(archive.getTrip()).isSameAs(trip);

        archive.linkTrip(null);
        assertThat(archive.getTrip()).isNull();
    }

    @Test
    @DisplayName("changeCover — 대표 사진을 바꾸거나 null로 없앤다")
    void changeCover_변경과_해제() {
        Archive archive = archive();
        ArchiveItem item = mock(ArchiveItem.class);

        archive.changeCover(item);
        assertThat(archive.getCoverItem()).isSameAs(item);

        archive.changeCover(null);
        assertThat(archive.getCoverItem()).isNull();
    }

    @Test
    @DisplayName("refreshPeriod — 사진 촬영일 범위로 기간을 다시 계산한다")
    void refreshPeriod_기간_재계산() {
        Archive archive = archive();
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 5);

        archive.refreshPeriod(start, end);

        assertThat(archive.getStartDate()).isEqualTo(start);
        assertThat(archive.getEndDate()).isEqualTo(end);
    }

    @Test
    @DisplayName("refreshPeriod — 사진이 없으면 null로 비운다")
    void refreshPeriod_사진없으면_null() {
        Archive archive = archive();
        archive.refreshPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));

        archive.refreshPeriod(null, null);

        assertThat(archive.getStartDate()).isNull();
        assertThat(archive.getEndDate()).isNull();
    }

    @Test
    @DisplayName("openCollaboration — NONE일 때만 OPEN으로 바뀐다")
    void openCollaboration_NONE에서만_전이() {
        Archive archive = archive();

        archive.openCollaboration();

        assertThat(archive.getCollaborationStatus()).isEqualTo(CollaborationStatus.OPEN);
    }

    @Test
    @DisplayName("openCollaboration — 이미 CLOSED면 그대로 유지한다 (재초대로 되살아나지 않음)")
    void openCollaboration_CLOSED는_그대로() {
        Archive archive = archive();
        archive.openCollaboration();
        archive.closeCollaboration();

        archive.openCollaboration();

        assertThat(archive.getCollaborationStatus()).isEqualTo(CollaborationStatus.CLOSED);
    }

    @Test
    @DisplayName("closeCollaboration — 편집이 막히고 isEditable이 false가 된다")
    void closeCollaboration_편집_막힘() {
        Archive archive = archive();
        archive.openCollaboration();

        archive.closeCollaboration();

        assertThat(archive.getCollaborationStatus()).isEqualTo(CollaborationStatus.CLOSED);
        assertThat(archive.isEditable()).isFalse();
    }

    @Test
    @DisplayName("isEditable — NONE·OPEN이면 편집 가능하다")
    void isEditable_NONE과_OPEN은_편집가능() {
        Archive archive = archive();
        assertThat(archive.isEditable()).isTrue();

        archive.openCollaboration();
        assertThat(archive.isEditable()).isTrue();
    }

    @Test
    @DisplayName("isOwnedBy — 소유자 id만 true다")
    void isOwnedBy_소유자만_true() {
        Archive archive = archive();

        assertThat(archive.isOwnedBy(OWNER_ID)).isTrue();
        assertThat(archive.isOwnedBy(999L)).isFalse();
    }
}
