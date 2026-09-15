package com.evergarden.evergardenbackend.archive.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchiveCollaboratorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    private Archive archive() {
        return mock(Archive.class);
    }

    private com.evergarden.evergardenbackend.user.entity.User user() {
        return mock(com.evergarden.evergardenbackend.user.entity.User.class);
    }

    @Test
    @DisplayName("owner — 초대 없이 바로 JOINED 상태로 만들어진다")
    void owner_바로_JOINED() {
        ArchiveCollaborator owner = ArchiveCollaborator.owner(archive(), user(), NOW);

        assertThat(owner.getRole()).isEqualTo(CollaboratorRole.OWNER);
        assertThat(owner.getStatus()).isEqualTo(CollaboratorStatus.JOINED);
        assertThat(owner.getInvitedAt()).isEqualTo(NOW);
        assertThat(owner.getJoinedAt()).isEqualTo(NOW);
        assertThat(owner.isOwner()).isTrue();
        assertThat(owner.canEdit()).isTrue();
    }

    @Test
    @DisplayName("invite — EDITOR·INVITED 상태로 만들어지고 아직 편집은 못 한다")
    void invite_INVITED_상태() {
        ArchiveCollaborator editor = ArchiveCollaborator.invite(archive(), user(), NOW);

        assertThat(editor.getRole()).isEqualTo(CollaboratorRole.EDITOR);
        assertThat(editor.getStatus()).isEqualTo(CollaboratorStatus.INVITED);
        assertThat(editor.getInvitedAt()).isEqualTo(NOW);
        assertThat(editor.getJoinedAt()).isNull();
        assertThat(editor.isOwner()).isFalse();
        assertThat(editor.canEdit()).isFalse();
    }

    @Test
    @DisplayName("accept — JOINED로 바뀌고 편집 가능해진다")
    void accept_JOINED_전이() {
        ArchiveCollaborator editor = ArchiveCollaborator.invite(archive(), user(), NOW);
        LocalDateTime acceptedAt = NOW.plusDays(1);

        editor.accept(acceptedAt);

        assertThat(editor.getStatus()).isEqualTo(CollaboratorStatus.JOINED);
        assertThat(editor.getJoinedAt()).isEqualTo(acceptedAt);
        assertThat(editor.canEdit()).isTrue();
    }

    @Test
    @DisplayName("leave — LEFT로 바뀌고 편집 권한을 잃는다")
    void leave_LEFT_전이() {
        ArchiveCollaborator editor = ArchiveCollaborator.invite(archive(), user(), NOW);
        editor.accept(NOW.plusDays(1));

        editor.leave();

        assertThat(editor.getStatus()).isEqualTo(CollaboratorStatus.LEFT);
        assertThat(editor.canEdit()).isFalse();
    }

    @Test
    @DisplayName("reinvite — LEFT였던 사람을 INVITED로 되돌리고 joinedAt을 지운다")
    void reinvite_INVITED로_복귀() {
        ArchiveCollaborator editor = ArchiveCollaborator.invite(archive(), user(), NOW);
        editor.accept(NOW.plusDays(1));
        editor.leave();

        LocalDateTime reinvitedAt = NOW.plusDays(10);
        editor.reinvite(reinvitedAt);

        assertThat(editor.getStatus()).isEqualTo(CollaboratorStatus.INVITED);
        assertThat(editor.getInvitedAt()).isEqualTo(reinvitedAt);
        assertThat(editor.getJoinedAt()).isNull();
        assertThat(editor.canEdit()).isFalse();
    }
}
