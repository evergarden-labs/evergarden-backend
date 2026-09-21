package com.evergarden.evergardenbackend.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.report.dto.ReportRequest;
import com.evergarden.evergardenbackend.report.dto.ReportResult;
import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportReason;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.repository.ReportRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** 신고 접수(COMM-10·18)의 검증 순서를 확인한다. */
class ReportServiceTest {

    private static final Long USER_ID = 1L;

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final ReportService reportService =
            new ReportService(reportRepository, postRepository, commentRepository, userRepository);

    private User reporter;
    private User targetAuthor;

    @BeforeEach
    void setUp() {
        reporter = user(USER_ID);
        targetAuthor = user(2L);
        given(userRepository.getReferenceById(USER_ID)).willReturn(reporter);
    }

    private User user(Long id) {
        User user = User.builder().nickname("여행자" + id).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Post post(Long id) {
        Post post = Post.builder().author(targetAuthor).content("게시물 내용").shareType(ShareType.ARCHIVE).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private Comment comment(Long id) {
        Comment comment = Comment.on(post(5L), targetAuthor, "댓글 내용");
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    // ── 사유 검증 ────────────────────────────────────────────

    @Test
    @DisplayName("사유가 ETC인데 설명이 없으면 INVALID_REQUEST — 대상을 조회하지 않는다")
    void ETC_설명없음() {
        ReportRequest request = new ReportRequest(ReportReason.ETC, null);

        assertThatThrownBy(() -> reportService.reportPost(USER_ID, 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
        verify(postRepository, never()).findById(any());
    }

    @Test
    @DisplayName("ETC여도 설명이 있으면 통과한다")
    void ETC_설명있음() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        ReportRequest request = new ReportRequest(ReportReason.ETC, "부적절한 사진이 있어요");

        ReportResult result = reportService.reportPost(USER_ID, 5L, request);

        assertThat(result.status()).isEqualTo(ReportStatus.PENDING);
    }

    // ── 게시물 신고(COMM-10) ─────────────────────────────────

    @Test
    @DisplayName("없는 게시물을 신고하면 POST_NOT_FOUND")
    void 게시물신고_없는게시물() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());
        ReportRequest request = new ReportRequest(ReportReason.SPAM, null);

        assertThatThrownBy(() -> reportService.reportPost(USER_ID, 99L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 게시물을 신고하면 POST_NOT_FOUND")
    void 게시물신고_삭제된게시물() {
        Post post = post(5L);
        post.delete();
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        ReportRequest request = new ReportRequest(ReportReason.SPAM, null);

        assertThatThrownBy(() -> reportService.reportPost(USER_ID, 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 신고한 게시물이면 DB 제약 위반을 ALREADY_REPORTED로 바꾼다")
    void 게시물신고_중복() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        given(reportRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("dup"));
        ReportRequest request = new ReportRequest(ReportReason.SPAM, null);

        assertThatThrownBy(() -> reportService.reportPost(USER_ID, 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REPORTED);
    }

    @Test
    @DisplayName("정상 신고는 PENDING 상태로 접수되고, 대상 작성자를 신고 대상으로 남긴다")
    void 게시물신고_정상() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        ReportRequest request = new ReportRequest(ReportReason.OBSCENE, "부적절한 사진");

        ReportResult result = reportService.reportPost(USER_ID, 5L, request);

        assertThat(result.status()).isEqualTo(ReportStatus.PENDING);
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTargetUser()).isEqualTo(targetAuthor);
        assertThat(captor.getValue().getReporter()).isEqualTo(reporter);
    }

    // ── 댓글·대댓글 신고(COMM-18) ────────────────────────────

    @Test
    @DisplayName("없는 댓글을 신고하면 COMMENT_NOT_FOUND")
    void 댓글신고_없는댓글() {
        given(commentRepository.findById(99L)).willReturn(Optional.empty());
        ReportRequest request = new ReportRequest(ReportReason.ABUSE, null);

        assertThatThrownBy(() -> reportService.reportComment(USER_ID, 99L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 댓글 신고는 PENDING으로 접수된다")
    void 댓글신고_정상() {
        Comment comment = comment(10L);
        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));
        ReportRequest request = new ReportRequest(ReportReason.ABUSE, null);

        ReportResult result = reportService.reportComment(USER_ID, 10L, request);

        assertThat(result.status()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("대댓글 신고도 댓글과 같은 로직으로 접수된다")
    void 대댓글신고_정상() {
        Comment parentComment = comment(10L);
        Comment reply = Comment.replyTo(parentComment, targetAuthor, "답글");
        ReflectionTestUtils.setField(reply, "id", 11L);
        given(commentRepository.findById(11L)).willReturn(Optional.of(reply));
        ReportRequest request = new ReportRequest(ReportReason.FALSE_INFO, null);

        ReportResult result = reportService.reportReply(USER_ID, 11L, request);

        assertThat(result.status()).isEqualTo(ReportStatus.PENDING);
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTargetType())
                .isEqualTo(com.evergarden.evergardenbackend.report.entity.ReportTargetType.COMMENT);
    }
}
