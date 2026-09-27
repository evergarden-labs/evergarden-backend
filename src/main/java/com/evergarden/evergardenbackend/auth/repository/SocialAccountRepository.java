package com.evergarden.evergardenbackend.auth.repository;

import com.evergarden.evergardenbackend.auth.entity.SocialAccount;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
