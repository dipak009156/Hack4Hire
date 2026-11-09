package com.quantumlock.request;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String subject;

    @Column(name = "body_html", nullable = false, columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(name = "email_type", nullable = false)
    private String emailType; // ADMIN_ALERT, APPROVAL_NOTICE, REJECTION_NOTICE

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "sent_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant sentAt = Instant.now();
}

