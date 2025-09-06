package org.kh.motorbikesharingsystem_be.repository;

import org.kh.motorbikesharingsystem_be.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepos extends JpaRepository<Users, Long> {
    Users findByEmail(String email);
}
