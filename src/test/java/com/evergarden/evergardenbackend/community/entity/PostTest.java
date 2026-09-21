package com.evergarden.evergardenbackend.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** {@code Post} 엔티티의 순수 로직(공유 대상 유실 판정·카운터·상태 전환)을 확인한다. */
class PostTest {

    private User user(Long id) {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Trip trip() {
        return Trip.builder().owner(user(1L)).title("제주 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 3)).build();
    }

    private Archive archive() {
        return Archive.builder().owner(user(1L)).title("제주 앨범").theme(ArchiveTheme.POLAROID).build();
    }

    private Post post(ShareType shareType, Trip trip, Archive archive) {
        return Post.builder().author(user(1L)).content("내용").shareType(shareType)
                .sharedTrip(trip).sharedArchive(archive).build();
    }

    // ── 공유 대상 유실 판정(COMM-17) ─────────────────────────

    @Test
    void 코스공유인데_트립이없으면_유실() {
        Post post = post(ShareType.COURSE, null, null);

        assertThat(post.isSharedTripMissing()).isTrue();
    }

    @Test
    void 코스공유이고_트립이있으면_유실아님() {
        Post post = post(ShareType.COURSE, trip(), null);

        assertThat(post.isSharedTripMissing()).isFalse();
    }

    @Test
    void 아카이브만_공유했으면_트립유실_판정대상아님() {
        Post post = post(ShareType.ARCHIVE, null, archive());

        assertThat(post.isSharedTripMissing()).isFalse();
    }

    @Test
    void 아카이브공유인데_아카이브가없으면_유실() {
        Post post = post(ShareType.ARCHIVE, null, null);

        assertThat(post.isSharedArchiveMissing()).isTrue();
    }

    @Test
    void 코스만_공유했으면_아카이브유실_판정대상아님() {
        Post post = post(ShareType.COURSE, trip(), null);

        assertThat(post.isSharedArchiveMissing()).isFalse();
    }

    @Test
    void 둘다공유인데_둘다없으면_둘다유실() {
        Post post = post(ShareType.BOTH, null, null);

        assertThat(post.isSharedTripMissing()).isTrue();
        assertThat(post.isSharedArchiveMissing()).isTrue();
    }

    @Test
    void 둘다공유이고_하나만_없으면_그것만_유실() {
        Post post = post(ShareType.BOTH, trip(), null);

        assertThat(post.isSharedTripMissing()).isFalse();
        assertThat(post.isSharedArchiveMissing()).isTrue();
    }

    // ── 좋아요 카운터 ────────────────────────────────────────

    @Test
    void 좋아요를_누르면_1_늘어난다() {
        Post post = post(ShareType.ARCHIVE, null, archive());

        post.increaseLikeCount();

        assertThat(post.getLikeCount()).isEqualTo(1);
    }

    @Test
    void 좋아요_취소는_0_밑으로_안내려간다() {
        Post post = post(ShareType.ARCHIVE, null, archive());

        post.decreaseLikeCount();

        assertThat(post.getLikeCount()).isZero();
    }

    @Test
    void 좋아요를_누르고_취소하면_원래대로() {
        Post post = post(ShareType.ARCHIVE, null, archive());

        post.increaseLikeCount();
        post.increaseLikeCount();
        post.decreaseLikeCount();

        assertThat(post.getLikeCount()).isEqualTo(1);
    }

    // ── 댓글 카운터 ──────────────────────────────────────────

    @Test
    void 댓글이_달리면_1_늘어난다() {
        Post post = post(ShareType.ARCHIVE, null, archive());

        post.increaseCommentCount();

        assertThat(post.getCommentCount()).isEqualTo(1);
    }

    @Test
    void 댓글_감소도_0_밑으로_안내려간다() {
        Post post = post(ShareType.ARCHIVE, null, archive());

        post.decreaseCommentCount();

        assertThat(post.getCommentCount()).isZero();
    }

    // ── 상태 전환·소유자 ─────────────────────────────────────

    @Test
    void 삭제하면_상태만_바뀌고_내용은_남는다() {
        Post post = post(ShareType.ARCHIVE, null, archive());

        post.delete();

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getContent()).isEqualTo("내용");
    }

    @Test
    void 본문을_고친다() {
        Post post = post(ShareType.ARCHIVE, null, archive());

        post.updateContent("고친 내용");

        assertThat(post.getContent()).isEqualTo("고친 내용");
    }

    @Test
    void 작성자_아이디가_같으면_true() {
        Post post = post(ShareType.ARCHIVE, null, archive());

        assertThat(post.isWrittenBy(1L)).isTrue();
        assertThat(post.isWrittenBy(2L)).isFalse();
    }
}
