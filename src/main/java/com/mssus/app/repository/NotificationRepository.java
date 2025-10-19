package com.mssus.app.repository;

import com.mssus.app.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    @Query("SELECT n FROM Notification n WHERE n.user.userId = :userId")
    List<Notification> findByUserId(Integer userId);

    List<Notification> findByUser_UserIdAndIsReadFalse(Integer userId);
}
