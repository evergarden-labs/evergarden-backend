package com.evergarden.evergardenbackend.user.repository;

import com.evergarden.evergardenbackend.user.entity.Admin;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    /** 로그인 시 아이디로 찾는다(ADMIN-01). */
    Optional<Admin> findByLoginId(String loginId);
}
