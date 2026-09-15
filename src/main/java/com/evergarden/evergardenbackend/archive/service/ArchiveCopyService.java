package com.evergarden.evergardenbackend.archive.service;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import com.evergarden.evergardenbackend.archive.entity.ArchiveLayoutSlot;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 아카이브 복제(ARCH-15)와 공유 아카이브 가져오기(ARCH-16). */
@Service
@RequiredArgsConstructor
@Transactional
public class ArchiveCopyService {

    private final ArchiveRepository archiveRepository;
    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveCollaboratorRepository collaboratorRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ArchiveAccessGuard accessGuard;
    private final ArchiveMapper archiveMapper;

    /**
     * 함께 만든 아카이브를 내용까지 통째로 복제한다. 종료된 공동 편집도 복제할 수 있다(ADR-039) —
     * 그래서 {@link ArchiveAccessGuard#checkViewable}만 확인하고 {@code checkEditable}은 안 쓴다.
     */
    public ArchiveDetail duplicate(Long userId, Long archiveId, String requestedTitle) {
        Archive original = findArchive(archiveId);
        accessGuard.checkViewable(original, userId);

        User newOwner = userRepository.getReferenceById(userId);
        String title = (requestedTitle != null && !requestedTitle.isBlank())
                ? requestedTitle : original.getTitle();

        Archive copy = Archive.builder()
                .owner(newOwner)
                .title(title)
                .theme(original.getTheme())
                .primaryColor(original.getPrimaryColor())
                .originArchive(original)
                .build();
        archiveRepository.save(copy);
        collaboratorRepository.save(ArchiveCollaborator.owner(copy, newOwner, LocalDateTime.now()));

        copyItems(original, copy);
        copy.refreshPeriod(original.getStartDate(), original.getEndDate());

        return toDetail(copy, userId);
    }

    /**
     * 커뮤니티 게시물에 공유된 아카이브의 틀만 가져온다. 사진·영상·지역·캡션은 가져오지 않고
     * 배치·순서만 {@code layoutTemplate}으로 남긴다(ADR-057) — {@link ArchiveItem}은 만들지 않는다.
     */
    public ArchiveDetail importShared(Long userId, Long postId, String title) {
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Archive original = post.getSharedArchive();
        if (original == null) {
            throw new BusinessException(ErrorCode.ARCHIVE_NOT_FOUND);
        }

        User newOwner = userRepository.getReferenceById(userId);
        Archive copy = Archive.builder()
                .owner(newOwner)
                .title(title)
                .theme(original.getTheme())
                .primaryColor(original.getPrimaryColor())
                .originArchive(original)
                .layoutTemplate(layoutTemplateOf(original))
                .build();
        archiveRepository.save(copy);
        collaboratorRepository.save(ArchiveCollaborator.owner(copy, newOwner, LocalDateTime.now()));

        return toDetail(copy, userId);
    }

    /** 항목·미디어 참조·대표 사진까지 그대로 복사한다. 미디어 자체는 새로 만들지 않고 같은 것을 가리킨다. */
    private void copyItems(Archive original, Archive copy) {
        List<ArchiveItem> originalItems = archiveItemRepository.findByArchiveOrderBySortOrderAsc(original);
        Map<Long, ArchiveItem> originalIdToCopy = new HashMap<>();
        List<ArchiveItem> copiedItems = new ArrayList<>();
        for (ArchiveItem item : originalItems) {
            ArchiveItem copied = ArchiveItem.builder()
                    .archive(copy)
                    .media(item.getMedia())
                    .sortOrder(item.getSortOrder())
                    .layout(item.getLayout())
                    .caption(item.getCaption())
                    .build();
            copiedItems.add(copied);
            originalIdToCopy.put(item.getId(), copied);
        }
        archiveItemRepository.saveAll(copiedItems);

        if (original.getCoverItem() != null) {
            copy.changeCover(originalIdToCopy.get(original.getCoverItem().getId()));
        }
    }

    private List<ArchiveLayoutSlot> layoutTemplateOf(Archive original) {
        return archiveItemRepository.findByArchiveOrderBySortOrderAsc(original).stream()
                .map(item -> new ArchiveLayoutSlot(item.getSortOrder(), item.getLayout()))
                .toList();
    }

    private Archive findArchive(Long archiveId) {
        return archiveRepository.findById(archiveId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARCHIVE_NOT_FOUND));
    }

    private ArchiveDetail toDetail(Archive archive, Long userId) {
        List<ArchiveItem> items = archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive);
        List<ArchiveCollaborator> collaborators = collaboratorRepository.findByArchive(archive);
        return archiveMapper.toDetail(archive, items, collaborators, userId);
    }
}
