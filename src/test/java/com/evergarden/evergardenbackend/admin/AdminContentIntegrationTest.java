package com.evergarden.evergardenbackend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 Postgres로 V8 마이그레이션(posts·comments의 deleted_by_admin_id·delete_reason)이
 * 정상 적용되고, 강제 삭제 후 누가 왜 지웠는지 실제로 읽히는지 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
class AdminContentIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;

    @Test
    @DisplayName("게시물 강제 삭제 → DB에 누가 왜 지웠는지 남는다")
    void 게시물_강제삭제_흐름() throws Exception {
        Admin admin = adminRepository.save(Admin.builder()
                .loginId("ops-" + System.nanoTime()).passwordHash("hash").name("담당자").build());
        String token = tokenProvider.issueAccessToken(admin.getId(), Role.ADMIN);
        User author = userRepository.save(User.builder().nickname("작성자a").build());
        Post post = postRepository.save(Post.builder()
                .author(author).content("문제 내용").shareType(ShareType.ARCHIVE).build());

        mvc.perform(delete("/admin/posts/" + post.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"정책 위반"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        Post reloaded = postRepository.findById(post.getId()).orElseThrow();
        Assertions.assertThat(reloaded.isDeleted()).isTrue();
        Assertions.assertThat(reloaded.getDeletedByAdmin().getId()).isEqualTo(admin.getId());
        Assertions.assertThat(reloaded.getDeleteReason()).isEqualTo("정책 위반");
    }

    @Test
    @DisplayName("대댓글 강제 삭제 → 자리는 남고 내용만 비워지며 누가 왜 지웠는지 남는다")
    void 대댓글_강제삭제_흐름() throws Exception {
        Admin admin = adminRepository.save(Admin.builder()
                .loginId("ops-" + System.nanoTime()).passwordHash("hash").name("담당자").build());
        String token = tokenProvider.issueAccessToken(admin.getId(), Role.ADMIN);
        User author = userRepository.save(User.builder().nickname("작성자b").build());
        Post post = postRepository.save(Post.builder()
                .author(author).content("내용").shareType(ShareType.ARCHIVE).build());
        Comment parent = commentRepository.save(Comment.on(post, author, "부모 댓글"));
        Comment reply = commentRepository.save(Comment.replyTo(parent, author, "대댓글 내용"));

        mvc.perform(delete("/admin/replies/" + reply.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"욕설"}
                                """))
                .andExpect(status().isOk());

        Comment reloaded = commentRepository.findById(reply.getId()).orElseThrow();
        Assertions.assertThat(reloaded.isDeleted()).isTrue();
        Assertions.assertThat(reloaded.getContent()).isNull();
        Assertions.assertThat(reloaded.getDeletedByAdmin().getId()).isEqualTo(admin.getId());
        Assertions.assertThat(reloaded.getDeleteReason()).isEqualTo("욕설");
        // 부모 댓글 자리는 그대로 남아 있어야 한다(ADR-007)
        Assertions.assertThat(commentRepository.findById(parent.getId())).isPresent();
    }
}
