package com.evergarden.evergardenbackend.user.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 계정.
 *
 * <p>일반 회원({@link User})과 저장소를 나눈 이유는 소셜 로그인 전용 정책을 지키기 위해서다.
 * 인증 토큰은 같은 Bearer 스킴을 쓰고 역할 클레임으로 구분한다(ADR-036).
 */
@Entity
@Getter
@Table(name = "admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 50, unique = true)
    private String loginId;

    /** BCrypt 해시 */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 제재 이력에 남길 담당자 이름 */
    @Column(nullable = false, length = 30)
    private String name;

    @Builder
    private Admin(String loginId, String passwordHash, String name) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
