package com.evergarden.evergardenbackend.archive.repository;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.CollaboratorStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArchiveCollaboratorRepository extends JpaRepository<ArchiveCollaborator, Long> {

    List<ArchiveCollaborator> findByArchive(Archive archive);

    Optional<ArchiveCollaborator> findByArchiveAndUser_Id(Archive archive, Long userId);

    /** 지금 관여 중인(참여든 소유든) 모든 아카이브. 탈퇴 시 공동 편집 정리에 쓴다(ADR-054). */
    List<ArchiveCollaborator> findByUser_IdAndStatus(Long userId, CollaboratorStatus status);

    Optional<ArchiveCollaborator> findByArchive_IdAndUser_Id(Long archiveId, Long userId);

    /**
     * {@code user}를 즉시 로딩해서 가져온다. STOMP 이벤트 리스너처럼 트랜잭션·영속성
     * 컨텍스트 밖에서 {@code CollaboratorResponse}로 바로 매핑해야 할 때 쓴다 —
     * 지연 로딩 프록시는 세션이 끝난 뒤엔 초기화할 수 없다({@code LazyInitializationException}).
     */
    @Query("select c from ArchiveCollaborator c join fetch c.user where c.archive.id = :archiveId and c.user.id = :userId")
    Optional<ArchiveCollaborator> findByArchive_IdAndUser_IdWithUser(
            @Param("archiveId") Long archiveId, @Param("userId") Long userId);
}
