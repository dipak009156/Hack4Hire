package com.quantumlock.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailNotificationRepository extends JpaRepository<EmailNotification, UUID> {
    List<EmailNotification> findAllByOrderBySentAtDesc();
    List<EmailNotification> findByRecipientEmailOrderBySentAtDesc(String recipientEmail);
    List<EmailNotification> findByRequestIdOrderBySentAtDesc(UUID requestId);
}

