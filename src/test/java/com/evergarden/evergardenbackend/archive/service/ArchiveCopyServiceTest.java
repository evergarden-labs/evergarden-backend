package com.evergarden.evergardenbackend.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import com.evergarden.evergardenbackend.archive.entity.ArchiveLayout;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import com.evergarden.evergardenbackend.media.service.MediaMapper;
import com.evergarden.evergardenbackend.media.service.MediaStorageService;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 아카이브 복제(ARCH-15)와 공유 아카이브 가져오기(ARCH-16). */
class ArchiveCopyServiceTest {

    private static final Long USER_ID = 1L;

    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final ArchiveItemRepository archiveItemRepository = mock(ArchiveItemRepository.class);
    private final ArchiveCollaboratorRepository collaboratorRepository = mock(ArchiveCollaboratorRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ArchiveAccessGuard accessGuard = mock(ArchiveAccessGuard.class);
    private final ArchiveMapper archiveMapper =
            new ArchiveMapper(new MediaMapper(mock(MediaStorageService.class)));

    private final ArchiveCopyService service = new ArchiveCopyService(
            archiveRepository, archiveItemRepository, collaboratorRepository,
            postRepository, userRepository, accessGuard, archiveMapper);

    private final AtomicLong nextId = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(user);
        given(collaboratorRepository.findByArchive(any())).willReturn(List.of());
        // IDENTITY 전략을 흉내낸다
        given(archiveRepository.save(any())).willAnswer(inv -> {
            Archive a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", nextId.getAndIncrement());
            return a;
        });
    }

    private Archive originalArchive(int itemCount, boolean withCover) {
        User owner = User.builder().nickname("원주인").build();
        ReflectionTestUtils.setField(owner, "id", 999L);
        Archive archive = Archive.builder().owner(owner).title("원본").theme(ArchiveTheme.POLAROID)
                .primaryColor("#ABCDEF").build();
        ReflectionTestUtils.setField(archive, "id", 1L);
        ReflectionTestUtils.setField(archive, "startDate", LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(archive, "endDate", LocalDate.of(2026, 1, 5));

        User uploader = User.builder().nickname("업로더").build();
        ReflectionTestUtils.setField(uploader, "id", 999L);
        List<ArchiveItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            Media media = Media.builder().uploader(uploader).type(MediaType.IMAGE)
                    .storageKey("k" + i).sizeBytes(1L).build();
            ReflectionTestUtils.setField(media, "id", (long) (200 + i));
            ArchiveItem item = ArchiveItem.builder().archive(archive).media(media)
                    .sortOrder((short) (i + 1)).layout(ArchiveLayout.of(0, 0, 0.5, 0.5)).caption("c" + i).build();
            ReflectionTestUtils.setField(item, "id", (long) (10 + i));
            items.add(item);
        }
        given(archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive)).willReturn(items);
        if (withCover && !items.isEmpty()) {
            archive.changeCover(items.get(0));
        }
        return archive;
    }

    // ── duplicate ────────────────────────────────────────────────

    @Test
    @DisplayName("참여한 적 없으면 접근 자체가 거절된다")
    void 참여한적_없으면_거절() {
        Archive original = originalArchive(0, false);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(original));
        doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(accessGuard).checkViewable(original, USER_ID);

        assertThatThrownBy(() -> service.duplicate(USER_ID, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("제목을 생략하면 원본 제목을 그대로 쓴다")
    void 제목_생략() {
        Archive original = originalArchive(0, false);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(original));

        ArchiveDetail result = service.duplicate(USER_ID, 1L, null);

        assertThat(result.title()).isEqualTo("원본");
    }

    @Test
    @DisplayName("제목을 보내면 그 제목을 쓴다")
    void 제목_지정() {
        Archive original = originalArchive(0, false);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(original));

        ArchiveDetail result = service.duplicate(USER_ID, 1L, "내 복사본");

        assertThat(result.title()).isEqualTo("내 복사본");
    }

    @Test
    @DisplayName("항목·대표 사진·기간까지 통째로 복사되고 원본을 가리킨다")
    void 항목과_대표사진_복사() {
        Archive original = originalArchive(2, true);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(original));
        // 새로 만들어지는 아카이브의 항목은 saveAll로 넘어온 것을 그대로 캡처해서 확인한다 —
        // toDetail이 다시 조회하는 findByArchiveOrderBySortOrderAsc는 새 아카이브 인스턴스에 대해
        // 스텁을 안 걸어서 항상 빈 목록이라, 응답이 아니라 saveAll 인자로 검증한다
        List<ArchiveItem> capturedCopies = new java.util.ArrayList<>();
        given(archiveItemRepository.saveAll(any())).willAnswer(inv -> {
            List<ArchiveItem> items = inv.getArgument(0);
            long id = 500L;
            for (ArchiveItem item : items) {
                ReflectionTestUtils.setField(item, "id", id++);
            }
            capturedCopies.addAll(items);
            return items;
        });

        ArchiveDetail result = service.duplicate(USER_ID, 1L, null);

        assertThat(result.originArchiveId()).isEqualTo(1L);
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(capturedCopies).hasSize(2);
        assertThat(capturedCopies.get(0).getCaption()).isEqualTo("c0");
        assertThat(capturedCopies).allMatch(item -> item.getArchive() != original);
    }

    // ── importShared ─────────────────────────────────────────────

    @Test
    @DisplayName("없는 게시물이면 POST_NOT_FOUND")
    void 없는_게시물() {
        given(postRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.importShared(USER_ID, 1L, "제목"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 게시물도 POST_NOT_FOUND")
    void 삭제된_게시물() {
        Post deleted = Post.builder().author(mock(User.class)).content("c").shareType(ShareType.ARCHIVE).build();
        deleted.delete();
        given(postRepository.findById(1L)).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.importShared(USER_ID, 1L, "제목"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("공유된 아카이브가 없으면(코스만 공유했거나 원본이 삭제됨) ARCHIVE_NOT_FOUND")
    void 공유된_아카이브_없음() {
        Post post = Post.builder().author(mock(User.class)).content("c").shareType(ShareType.COURSE).build();
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> service.importShared(USER_ID, 1L, "제목"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ARCHIVE_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 가져오기는 items가 비어있고 layoutTemplate만 채워진다")
    void 정상_가져오기() {
        Archive original = originalArchive(2, false);
        Post post = Post.builder().author(mock(User.class)).content("c")
                .shareType(ShareType.ARCHIVE).sharedArchive(original).build();
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        ArchiveDetail result = service.importShared(USER_ID, 1L, "가져온 제목");

        assertThat(result.title()).isEqualTo("가져온 제목");
        assertThat(result.originArchiveId()).isEqualTo(1L);
        assertThat(result.items()).isEmpty();
        assertThat(result.layoutTemplate()).hasSize(2);
        assertThat(result.layoutTemplate().get(0).sortOrder()).isEqualTo((short) 1);
    }
}
