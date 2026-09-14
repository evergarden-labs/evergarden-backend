package com.evergarden.evergardenbackend.global.security;

import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인이 없어도 도메인 API를 바로 써볼 수 있게 고정 사용자 하나를 만들어 둔다.
 *
 * <p>{@code dev-auth.enabled=true}일 때만 뜬다. {@code dev} 프로필에서만 로드되고,
 * application-prod.yml이 이 값을 명시적으로 {@code false}로 고정해 운영에는 절대 켜지지 않는다.
 * 소셜 로그인이 생긴 뒤에도 {@code dev-auth.enabled}를 켜 두면, 토큰을 들고 오는 요청은
 * 그대로 검증되고 <b>토큰이 없는 요청만</b> 이 사용자로 통과한다({@link JwtAuthenticationFilter}).
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "dev-auth", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevUserSeeder implements ApplicationRunner {

    private static final String DEV_NICKNAME = "devuser";

    private final UserRepository userRepository;
    private final DevUserProvider devUserProvider;

    @Override
    public void run(ApplicationArguments args) {
        User user = userRepository.findByNickname(DEV_NICKNAME).orElseGet(this::createDevUser);
        devUserProvider.set(user.getId());
        log.warn("dev-auth 활성화 — 토큰 없는 요청은 전부 devuser(id={})로 인증됩니다. "
                + "운영 프로필에서는 절대 켜지 마세요.", user.getId());
    }

    private User createDevUser() {
        User user = User.builder().nickname(DEV_NICKNAME).build();
        user.skipOnboarding();
        return userRepository.save(user);
    }
}
