package com.evergarden.evergardenbackend.user.repository;

import com.evergarden.evergardenbackend.user.entity.Admin;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {
}
