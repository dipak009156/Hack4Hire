package com.quantumlock.request;

import java.time.Instant;
import java.util.UUID;

public record EmailNotificationResponse(
        UUID id,
        String recipientEmail,
        String recipientName,
        String subject,
        String bodyHtml,
        String emailType,
        UUID requestId,
        boolean isRead,
        Instant sentAt
) {
    public static EmailNotificationResponse fromEntity(EmailNotification e) {
        return new EmailNotificationResponse(
                e.getId(),
                e.getRecipientEmail(),
                e.getRecipientName(),
                e.getSubject(),
                e.getBodyHtml(),
                e.getEmailType(),
                e.getRequestId(),
                e.isRead(),
                e.getSentAt()
        );
    }
}

