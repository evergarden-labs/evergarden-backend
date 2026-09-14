package com.evergarden.evergardenbackend.user.repository;

import com.evergarden.evergardenbackend.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);
}
