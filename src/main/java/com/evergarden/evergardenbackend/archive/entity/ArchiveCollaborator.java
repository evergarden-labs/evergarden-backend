package com.evergarden.evergardenbackend.archive.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import com.evergarden.evergardenbackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아카이브 공동 편집자.
 *
 * <p>초대는 닉네임으로 사람을 지목해서 한다(ADR-024). 링크·코드 방식이 아니라
 * 상대가 이미 가입해 있어야 초대할 수 있다.
 */
@Entity
@Getter
@Table(
        name = "archive_collaborators",
        uniqueConstraints = @UniqueConstraint(
                name = "ac_uk", columnNames = {"archive_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveCollaborator extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "archive_id", nullable = false)
    private Archive archive;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CollaboratorRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CollaboratorStatus status;

    @Column(name = "invited_at", nullable = false)
    private LocalDateTime invitedAt;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    private ArchiveCollaborator(Archive archive, User user, CollaboratorRole role,
                                CollaboratorStatus status, LocalDateTime invitedAt) {
        this.archive = archive;
        this.user = user;
        this.role = role;
        this.status = status;
        this.invitedAt = invitedAt;
    }

    /** 아카이브를 만든 사람. 초대 없이 바로 참여 상태다. */
    public static ArchiveCollaborator owner(Archive archive, User user, LocalDateTime now) {
        ArchiveCollaborator c =
                new ArchiveCollaborator(archive, user, CollaboratorRole.OWNER, CollaboratorStatus.JOINED, now);
        c.joinedAt = now;
        return c;
    }

    /** 초대장을 보낸다(ARCH-10). 상대가 수락해야 편집 권한이 생긴다. */
    public static ArchiveCollaborator invite(Archive archive, User user, LocalDateTime now) {
        return new ArchiveCollaborator(archive, user, CollaboratorRole.EDITOR, CollaboratorStatus.INVITED, now);
    }

    /** 초대를 수락한다(ARCH-11). */
    public void accept(LocalDateTime now) {
        this.status = CollaboratorStatus.JOINED;
        this.joinedAt = now;
    }

    /**
     * 초대를 거절하거나(ARCH-18) 참여 중이던 아카이브에서 나간다(ARCH-13).
     *
     * <p>내가 만든 부분은 아카이브에 남는다. 보관하려면 나가기 전에 복제해야 한다(ARCH-15).
     */
    public void leave() {
        this.status = CollaboratorStatus.LEFT;
    }

    /** 편집 권한이 있는지. 아카이브가 종료됐는지는 {@link Archive#isEditable()}이 따로 본다. */
    public boolean canEdit() {
        return status == CollaboratorStatus.JOINED;
    }

    public boolean isOwner() {
        return role == CollaboratorRole.OWNER;
    }
}
