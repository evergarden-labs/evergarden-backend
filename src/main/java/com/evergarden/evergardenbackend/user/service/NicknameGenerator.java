package com.evergarden.evergardenbackend.user.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "여행자####" 기본 닉네임을 만든다(ADR-050).
 *
 * <p>소셜 최초 가입(AUTH-01)은 온보딩 전에도 {@code users.nickname}이 {@code NOT NULL}이라
 * 임시값이 즉시 필요하다. 온보딩 건너뛰기(ONB-02)도 이 값을 그대로 최종 닉네임으로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class NicknameGenerator {

    private static final String PREFIX = "여행자";
    private static final int NUMBER_RANGE = 10_000;
    private static final int MAX_ATTEMPTS = 20;

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    /** 중복이면(ADR-025) 다른 숫자로 다시 만든다. */
    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = PREFIX + String.format("%04d", random.nextInt(NUMBER_RANGE));
            if (userRepository.findByNickname(candidate).isEmpty()) {
                return candidate;
            }
        }
        // 네 자리 조합(만 개) 중 스무 번 연속 겹칠 확률은 사실상 0에 가깝다 — 여기 닿으면
        // 사용자 수가 자릿수를 늘려야 할 만큼 늘었다는 신호다
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
