package com.evergarden.evergardenbackend.auth.repository;

import com.evergarden.evergardenbackend.auth.entity.SocialAccount;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
}
