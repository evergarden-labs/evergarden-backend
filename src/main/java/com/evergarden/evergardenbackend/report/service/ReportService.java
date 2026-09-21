package com.evergarden.evergardenbackend.report.service;

import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.report.dto.ReportRequest;
import com.evergarden.evergardenbackend.report.dto.ReportResult;
import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportReason;
import com.evergarden.evergardenbackend.report.entity.ReportTargetType;
import com.evergarden.evergardenbackend.report.repository.ReportRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시물·댓글·대댓글 신고(COMM-10·18). */
@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    /** 게시물을 신고한다(COMM-10). */
    public ReportResult reportPost(Long userId, Long postId, ReportRequest request) {
        validate(request);
        Post post = findActivePost(postId);
        return save(userId, ReportTargetType.POST, post.getId(), post.getAuthor(), request);
    }

    /** 댓글을 신고한다(COMM-18). */
    public ReportResult reportComment(Long userId, Long commentId, ReportRequest request) {
        return reportCommentOrReply(userId, commentId, request);
    }

    /**
     * 대댓글을 신고한다(COMM-18). 저장은 댓글 신고와 같은 테이블에 {@code targetType = COMMENT}로
     * 들어간다 — 경로가 나뉜 건 관리자 삭제(ADMIN-07·08)가 댓글·대댓글로 나뉘어 있어서일
     * 뿐, 저장 로직은 완전히 같다.
     */
    public ReportResult reportReply(Long userId, Long replyId, ReportRequest request) {
        return reportCommentOrReply(userId, replyId, request);
    }

    private ReportResult reportCommentOrReply(Long userId, Long commentId, ReportRequest request) {
        validate(request);
        Comment comment = findComment(commentId);
        return save(userId, ReportTargetType.COMMENT, comment.getId(), comment.getAuthor(), request);
    }

    /**
     * {@code (reporter, target)} 유니크를 DB에 맡긴다(ADR-006) — 미리 존재를 확인하면
     * 동시 요청 사이에서 새어 나간다. 좋아요 처리 때와 같은 이유·같은 패턴이다.
     */
    private ReportResult save(Long userId, ReportTargetType targetType, Long targetId, User targetUser,
                              ReportRequest request) {
        Report report = Report.builder()
                .reporter(userRepository.getReferenceById(userId))
                .targetType(targetType)
                .targetId(targetId)
                .targetUser(targetUser)
                .reason(request.reason())
                .detail(request.detail())
                .build();
        try {
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
        return new ReportResult(report.getId(), report.getStatus());
    }

    /** {@code reason}이 {@code ETC}인데 설명이 없으면 거부한다 — "기타"만으론 판정할 수 없다. */
    private void validate(ReportRequest request) {
        if (request.reason() == ReportReason.ETC && (request.detail() == null || request.detail().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Post findActivePost(Long postId) {
        return postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }
}
