package com.evergarden.evergardenbackend.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 운영 DB에 첫 관리자 계정을 직접 INSERT하기 위한 일회성 도구.
 *
 * <p>관리자는 가입 API가 없어(의도된 설계) 비밀번호를 BCrypt로 해싱해 SQL로 직접 넣어야 한다.
 * Spring 컨텍스트를 띄우지 않는 평범한 {@code main}이라 애플리케이션 실행 경로에는
 * 영향이 없다 — {@code ./gradlew generateAdminHash}로만 실행된다.
 *
 * <pre>
 *   ./gradlew generateAdminHash --args="로그인ID 비밀번호 담당자이름"
 * </pre>
 */
public final class GenerateAdminPasswordHash {

    private GenerateAdminPasswordHash() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("사용법: ./gradlew generateAdminHash --args=\"로그인ID 비밀번호 담당자이름\"");
            System.exit(1);
        }
        String loginId = args[0];
        String password = args[1];
        String name = args[2];

        String hash = new BCryptPasswordEncoder().encode(password);

        System.out.println();
        System.out.println("BCrypt 해시:");
        System.out.println(hash);
        System.out.println();
        System.out.println("아래 SQL을 운영 DB(RDS)에 직접 실행하세요:");
        System.out.println();
        System.out.printf(
                "INSERT INTO admins (login_id, password_hash, name, created_at, updated_at) "
                        + "VALUES ('%s', '%s', '%s', now(), now());%n",
                loginId.replace("'", "''"),
                hash,
                name.replace("'", "''"));
        System.out.println();
    }
}
