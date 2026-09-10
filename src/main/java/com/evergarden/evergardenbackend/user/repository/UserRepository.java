package com.evergarden.evergardenbackend.user.repository;

import com.evergarden.evergardenbackend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
