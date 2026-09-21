package com.evergarden.evergardenbackend.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 PostgreSQL·스프링 컨텍스트로 게시물 작성부터 좋아요·댓글·대댓글·신고까지
 * 전체 흐름이 실제로 엮여 도는지 확인한다. 시큐리티 필터·검증·서비스·리포지토리
 * 전부 진짜 빈이다.
 */
@AutoConfigureMockMvc
@Transactional
class CommunityIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;

    User author;
    User other;
    String authorToken;
    String otherToken;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder().nickname("작성자").build());
        other = userRepository.save(User.builder().nickname("이웃").build());
        authorToken = tokenProvider.issueAccessToken(author.getId(), Role.USER);
        otherToken = tokenProvider.issueAccessToken(other.getId(), Role.USER);
    }

    @Test
    @DisplayName("게시물 작성 → 좋아요 → 댓글 → 대댓글 → 신고까지 전체 흐름이 돈다")
    void 게시물_작성부터_신고까지() throws Exception {
        MvcResult tripResult = mvc.perform(post("/trips")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"서울 여행","startDate":"2026-03-01","endDate":"2026-03-01","regionCodes":["11"]}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Long tripId = at(tripResult, "/data/tripId");

        // 코스 공유 — 지역은 트립의 여행지에서 자동으로 합쳐져야 한다(ADR-003)
        MvcResult postResult = mvc.perform(post("/posts")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"서울 다녀왔어요\",\"tripId\":" + tripId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareType").value("COURSE"))
                .andExpect(jsonPath("$.data.regions[0].code").value("11"))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andReturn();
        Long postId = at(postResult, "/data/postId");

        // 좋아요 — 남이 누르면 게시물 좋아요 수가 늘고, 본인 조회엔 likedByMe=false
        mvc.perform(post("/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        mvc.perform(get("/posts/" + postId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(false));

        // 중복 좋아요는 DB 유니크 제약을 그대로 통과해 409여야 한다(ADR-006, 목이 아니라 실제 제약)
        mvc.perform(post("/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_LIKED"));

        // 댓글 — 작성하면 게시물의 commentCount가 오른다
        MvcResult commentResult = mvc.perform(post("/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"좋은 코스네요"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyCount").value(0))
                .andReturn();
        Long commentId = at(commentResult, "/data/commentId");

        mvc.perform(get("/posts/" + postId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(1));

        // 대댓글 — 부모 댓글의 replyCount·미리보기에 반영돼야 한다
        MvcResult replyResult = mvc.perform(post("/comments/" + commentId + "/replies")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"감사합니다!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentCommentId").value(commentId))
                .andReturn();
        Long replyId = at(replyResult, "/data/replyId");

        mvc.perform(get("/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].replyCount").value(1))
                .andExpect(jsonPath("$.data[0].replies[0].replyId").value(replyId));

        // 대댓글 삭제 — 댓글과 달리 목록에서 완전히 빠지고 commentCount도 줄어야 한다(COMM-16)
        mvc.perform(delete("/replies/" + replyId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk());

        mvc.perform(get("/comments/" + commentId + "/replies")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(get("/posts/" + postId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(1)); // 댓글은 그대로, 대댓글 1건만 줄어듦

        // 신고 — 접수는 PENDING, 같은 대상 중복 신고는 실제 DB 제약으로 막힌다
        mvc.perform(post("/posts/" + postId + "/reports")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"SPAM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mvc.perform(post("/posts/" + postId + "/reports")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"ABUSE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REPORTED"));
    }

    /**
     * 트립을 실제로 지우고 다시 조회하는 방식은 같은 트랜잭션 안에서 Hibernate의
     * 영속성 컨텍스트 그래프 정합성 검사에 걸린다({@code TripIntegrationTest}와 같은
     * 이유 — 실제 운영에서는 요청마다 세션이 독립적이라 벌어지지 않는 문제다).
     * 그래서 "원본이 이미 삭제된 상태"를 직접 재현해 {@code getPost}가 실제 서비스·
     * 리포지토리 스택을 통해 COMM-17을 맞게 처리하는지만 확인한다.
     */
    @Test
    @DisplayName("원본 코스가 삭제됐어도 게시물은 200이고 deletedShare에 COURSE가 담긴다(COMM-17)")
    void 원본삭제된_게시물_조회() throws Exception {
        Post post = postRepository.save(Post.builder().author(author).content("이제 없는 코스 공유")
                .shareType(ShareType.COURSE).build());

        mvc.perform(get("/posts/" + post.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sharedCourse").doesNotExist())
                .andExpect(jsonPath("$.data.deletedShare[0]").value("COURSE"));
    }

    private Long at(MvcResult result, String path) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).at(path).asLong();
    }
}
