package com.morphcode.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.morphcode.ai.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
