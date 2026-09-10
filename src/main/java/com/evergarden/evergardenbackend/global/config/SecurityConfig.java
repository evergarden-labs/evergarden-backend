package com.evergarden.evergardenbackend.global.config;

import com.evergarden.evergardenbackend.global.security.JwtAccessDeniedHandler;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationEntryPoint;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationFilter;
import com.evergarden.evergardenbackend.global.security.JwtProperties;
import com.evergarden.evergardenbackend.global.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 시큐리티 설정. 명세의 {@code security} 선언을 그대로 옮긴 것이다.
 *
 * <p>명세는 전역으로 {@code bearerAuth}를 걸고 인증이 필요 없는 오퍼레이션에만
 * {@code security: []}로 해제한다. 그래서 여기서도 <b>기본이 인증 필요</b>이고
 * 예외를 나열하는 방식으로 쓴다. 반대로 하면 새 오퍼레이션이 기본으로 열린다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 명세에서 {@code security: []}인 오퍼레이션 넷. 토큰을 받기 전에 부르는 것들이다.
     *
     * <p>{@code /auth/restore}가 여기 있는 이유 — 탈퇴한 회원은 토큰이 있어도
     * {@code USER_WITHDRAWN}으로 막히므로, 복구까지 막히면 되돌릴 방법이 없다(ADR-054).
     */
    private static final String[] PUBLIC_POST = {
            "/auth/social/*",        // AUTH-01 소셜 로그인
            "/auth/restore",         // AUTH-04 탈퇴 복구
            "/auth/token/refresh",   // AUTH-05 토큰 재발급
            "/admin/auth/login"      // ADMIN-01 관리자 로그인
    };

    /** API 문서. 운영에서는 springdoc 자체를 꺼서 404가 된다(application-prod.yml). */
    private static final String[] DOCS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/evergardenapi.yaml"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // 토큰 인증이라 세션·쿠키를 쓰지 않는다. 쿠키가 없으면 CSRF도 성립하지 않는다
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 스프링이 기본으로 붙이는 로그인 폼·기본 인증·로그아웃을 걷어낸다.
                // 이것들이 켜져 있으면 401 대신 로그인 페이지로 리다이렉트된다
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(DOCS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        // ADR-036 — 관리자 판별은 토큰의 역할 클레임으로 한다
                        .requestMatchers("/admin/**").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())

                // 필터 단계의 거절도 {"error":{...}} 봉투로 내보낸다.
                // 이걸 붙이지 않으면 오류 응답이 두 가지 모양이 된다
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 관리자 비밀번호 검증용({@code admins.password_hash}). 소셜 회원은 비밀번호가 없다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
