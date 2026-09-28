package com.evergarden.evergardenbackend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.admin.dto.AdminReportDetail;
import com.evergarden.evergardenbackend.admin.dto.ReportReviewResult;
import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.CommentStatus;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.RefreshTokenStore;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.service.NotificationService;
import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportReason;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.entity.ReportTargetType;
import com.evergarden.evergardenbackend.report.repository.ReportRepository;
import com.evergarden.evergardenbackend.report.repository.SanctionRepository;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 신고 관리·판정·자동 제재(ADMIN-14·15·09·10)를 다룬다. */
class AdminReportServiceTest {

    private static final Long ADMIN_ID = 9L;
    private static final Long TARGET_USER_ID = 2L;

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final SanctionRepository sanctionRepository = mock(SanctionRepository.class);
    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AdminReportService service = new AdminReportService(reportRepository, adminRepository,
            userRepository, postRepository, commentRepository, sanctionRepository, refreshTokenStore,
            notificationService);

    private User reporter;
    private User targetUser;
    private Admin admin;

    @BeforeEach
    void setUp() {
        reporter = User.builder().nickname("신고자").build();
        ReflectionTestUtils.setField(reporter, "id", 1L);
        targetUser = User.builder().nickname("대상자").build();
        ReflectionTestUtils.setField(targetUser, "id", TARGET_USER_ID);
        admin = Admin.builder().loginId("ops").passwordHash("hash").name("담당자").build();
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        given(adminRepository.getReferenceById(ADMIN_ID)).willReturn(admin);
        given(sanctionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private Report reportOf(ReportTargetType targetType, Long targetId) {
        Report report = Report.builder()
                .reporter(reporter).targetType(targetType).targetId(targetId).targetUser(targetUser)
                .reason(ReportReason.ABUSE).detail(null).build();
        ReflectionTestUtils.setField(report, "id", 100L);
        return report;
    }

    /** {@code reviewReport}가 원자적 증가 뒤 다시 읽는 값을 이 리턴값으로 흉내 낸다. */
    private void givenValidReportCountBecomes(int newCount) {
        given(userRepository.findValidReportCount(TARGET_USER_ID)).willReturn(newCount);
    }

    @Test
    @DisplayName("게시물이 살아 있으면 내용을 그대로 보여준다")
    void 상세_게시물_살아있음() {
        Report report = reportOf(ReportTargetType.POST, 10L);
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));
        Post post = mock(Post.class);
        given(post.getStatus()).willReturn(PostStatus.ACTIVE);
        given(post.getContent()).willReturn("원본 내용");
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        AdminReportDetail result = service.getReportDetail(100L);

        assertThat(result.targetContent()).isEqualTo("원본 내용");
        assertThat(result.targetDeleted()).isFalse();
    }

    @Test
    @DisplayName("게시물이 삭제됐으면 내용은 숨기고 targetDeleted만 true — 판정은 여전히 가능하다")
    void 상세_게시물_삭제됨() {
        Report report = reportOf(ReportTargetType.POST, 10L);
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));
        Post post = mock(Post.class);
        given(post.getStatus()).willReturn(PostStatus.DELETED);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        AdminReportDetail result = service.getReportDetail(100L);

        assertThat(result.targetContent()).isNull();
        assertThat(result.targetDeleted()).isTrue();
    }

    @Test
    @DisplayName("댓글(대댓글 포함) 대상도 같은 방식으로 처리한다")
    void 상세_댓글() {
        Report report = reportOf(ReportTargetType.COMMENT, 20L);
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));
        Comment comment = mock(Comment.class);
        given(comment.getStatus()).willReturn(CommentStatus.ACTIVE);
        given(comment.getContent()).willReturn("댓글 내용");
        given(commentRepository.findById(20L)).willReturn(Optional.of(comment));

        AdminReportDetail result = service.getReportDetail(100L);

        assertThat(result.targetContent()).isEqualTo("댓글 내용");
    }

    @Test
    @DisplayName("없는 신고면 REPORT_NOT_FOUND")
    void 상세_없는신고() {
        given(reportRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReportDetail(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("status가 PENDING이면 INVALID_REQUEST — 문법은 맞지만 허용되지 않는 판정값")
    void 판정_PENDING거절() {
        Report report = reportOf(ReportTargetType.POST, 10L);
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> service.reviewReport(ADMIN_ID, 100L, ReportStatus.PENDING, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("이미 판정된 신고면 REPORT_ALREADY_REVIEWED")
    void 판정_이미판정됨() {
        Report report = reportOf(ReportTargetType.POST, 10L);
        report.reject(admin, LocalDateTime.now(), "이미 반려됨");
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> service.reviewReport(ADMIN_ID, 100L, ReportStatus.VALID, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_ALREADY_REVIEWED);
    }

    @Test
    @DisplayName("반려는 누적 횟수를 안 올리고(원자적 증가 쿼리를 안 부름) 제재도 안 건다")
    void 판정_반려() {
        Report report = reportOf(ReportTargetType.POST, 10L);
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));
        Post post = mock(Post.class);
        given(post.getStatus()).willReturn(PostStatus.ACTIVE);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        ReportReviewResult result = service.reviewReport(ADMIN_ID, 100L, ReportStatus.REJECTED, "근거 부족");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(result.targetUserValidReportCount()).isZero();
        assertThat(result.appliedSanction()).isNull();
        verify(userRepository, never()).increaseValidReportCount(any());
        verify(userRepository, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("유효 판정으로 누적이 1이 되면 원자적 증가를 호출하고, 상태도 원자적 UPDATE로 WARNED로 바꾸고 알림을 보낸다")
    void 판정_유효_첫번째_자동경고() {
        Report report = reportOf(ReportTargetType.POST, 10L);
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));
        Post post = mock(Post.class);
        given(post.getStatus()).willReturn(PostStatus.ACTIVE);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        givenValidReportCountBecomes(1);

        ReportReviewResult result = service.reviewReport(ADMIN_ID, 100L, ReportStatus.VALID, null);

        assertThat(result.targetUserValidReportCount()).isEqualTo(1);
        assertThat(result.appliedSanction()).isNotNull();
        assertThat(result.appliedSanction().type().name()).isEqualTo("WARNING");
        verify(userRepository).increaseValidReportCount(TARGET_USER_ID);
        verify(userRepository).updateStatus(TARGET_USER_ID, UserStatus.WARNED);
        verify(notificationService).notify(
                eq(targetUser), eq(NotificationType.WARNING), any(), any(), eq(null), eq(null));
    }

    @Test
    @DisplayName("2번째 유효 판정은 아직 임계값이 아니라 상태 변경 없이 제재가 없다")
    void 판정_유효_두번째_제재없음() {
        Report report = reportOf(ReportTargetType.POST, 10L);
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));
        Post post = mock(Post.class);
        given(post.getStatus()).willReturn(PostStatus.ACTIVE);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        givenValidReportCountBecomes(2);

        ReportReviewResult result = service.reviewReport(ADMIN_ID, 100L, ReportStatus.VALID, null);

        assertThat(result.targetUserValidReportCount()).isEqualTo(2);
        assertThat(result.appliedSanction()).isNull();
        verify(userRepository, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("3번째 유효 판정에서 자동 차단이 걸리고 리프레시 토큰이 폐기된다")
    void 판정_유효_세번째_자동차단() {
        Report report = reportOf(ReportTargetType.POST, 10L);
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));
        Post post = mock(Post.class);
        given(post.getStatus()).willReturn(PostStatus.ACTIVE);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        givenValidReportCountBecomes(3);

        ReportReviewResult result = service.reviewReport(ADMIN_ID, 100L, ReportStatus.VALID, null);

        assertThat(result.targetUserValidReportCount()).isEqualTo(3);
        assertThat(result.appliedSanction().type().name()).isEqualTo("BLOCK");
        verify(userRepository).updateStatus(TARGET_USER_ID, UserStatus.BLOCKED);
        verify(refreshTokenStore).revoke(TARGET_USER_ID);
    }

    @Test
    @DisplayName("이미 3회를 넘겨 차단된 회원이 또 유효 판정을 받아도 제재를 반복해서 걸지 않는다")
    void 판정_유효_임계값초과_반복안함() {
        Report report = reportOf(ReportTargetType.POST, 10L);
        given(reportRepository.findById(100L)).willReturn(Optional.of(report));
        Post post = mock(Post.class);
        given(post.getStatus()).willReturn(PostStatus.ACTIVE);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        givenValidReportCountBecomes(4);

        ReportReviewResult result = service.reviewReport(ADMIN_ID, 100L, ReportStatus.VALID, null);

        assertThat(result.targetUserValidReportCount()).isEqualTo(4);
        assertThat(result.appliedSanction()).isNull();
        verify(userRepository, never()).updateStatus(any(), any());
        verify(refreshTokenStore, never()).revoke(any());
    }
}
