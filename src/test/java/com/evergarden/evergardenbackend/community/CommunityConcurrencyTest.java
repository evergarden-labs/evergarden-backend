package com.evergarden.evergardenbackend.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 좋아요·신고 중복 방지(ADR-006)가 진짜 동시 요청에서도 DB 유니크 제약으로
 * 막히는지 확인한다. 같은 자원을 두고 여러 스레드가 실제로 경합해야 의미가
 * 있어서, 다른 통합 테스트와 달리 {@code @Transactional}을 클래스에 안 건다 —
 * 걸면 모든 스레드가 테스트 스레드의 트랜잭션에 묶여 경합 자체가 안 생긴다.
 * 대신 컨테이너가 테스트마다 새로 뜨니 남는 데이터를 신경 쓸 필요는 없다.
 */
@AutoConfigureMockMvc
class CommunityConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 10;

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;

    @Test
    @DisplayName("같은 사용자가 같은 게시물에 동시에 좋아요를 여러 번 보내도 하나만 성공한다(ADR-006)")
    void 동시_좋아요_하나만_성공() throws Exception {
        User author = userRepository.save(User.builder().nickname("작성자좋아요동시성").build());
        User liker = userRepository.save(User.builder().nickname("이웃좋아요동시성").build());
        String likerToken = tokenProvider.issueAccessToken(liker.getId(), Role.USER);
        Post post = postRepository.save(Post.builder().author(author).content("내용")
                .shareType(ShareType.ARCHIVE).build());

        List<Integer> statuses = fireConcurrently(() -> mvc.perform(
                        post("/posts/" + post.getId() + "/like")
                                .header("Authorization", "Bearer " + likerToken))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(THREADS - 1);

        Post refreshed = postRepository.findById(post.getId()).orElseThrow();
        assertThat(refreshed.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사용자가 같은 게시물을 동시에 여러 번 신고해도 하나만 접수된다(ADR-006)")
    void 동시_신고_하나만_접수() throws Exception {
        User author = userRepository.save(User.builder().nickname("작성자신고동시성").build());
        User reporter = userRepository.save(User.builder().nickname("이웃신고동시성").build());
        String reporterToken = tokenProvider.issueAccessToken(reporter.getId(), Role.USER);
        Post post = postRepository.save(Post.builder().author(author).content("내용")
                .shareType(ShareType.ARCHIVE).build());

        List<Integer> statuses = fireConcurrently(() -> mvc.perform(
                        post("/posts/" + post.getId() + "/reports")
                                .header("Authorization", "Bearer " + reporterToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"reason":"SPAM"}
                                        """))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(THREADS - 1);
    }

    /** {@link #THREADS}개의 스레드가 동시에 같은 요청을 보내고, 각자 받은 HTTP 상태 코드를 모은다. */
    private List<Integer> fireConcurrently(Callable<Integer> request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return request.call();
            }));
        }

        ready.await();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return statuses;
    }
}
