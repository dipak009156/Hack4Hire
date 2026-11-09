package com.quantumlock.request;

import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.*;
import com.quantumlock.common.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CredentialRequestService {

    private static final Logger log = LoggerFactory.getLogger(CredentialRequestService.class);

    private final CredentialRequestRepository requestRepository;
    private final EmailNotificationRepository emailRepository;
    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PlatformEmailService platformEmailService;

    @Transactional(readOnly = true)
    public Map<String, Object> checkTenantAvailability(String name) {
        if (name == null || name.trim().isBlank()) {
            return Map.of("name", "", "available", false, "message", "Tenant name cannot be empty");
        }
        String normalized = name.trim().toLowerCase();

        boolean registered = tenantRepository.existsByNameIgnoreCase(normalized);
        if (registered) {
            return Map.of(
                    "name", normalized,
                    "available", false,
                    "exists", true,
                    "message", "Tenant name '" + normalized + "' is already registered. Please choose a unique name."
            );
        }

        boolean pending = requestRepository.findByTenantNameIgnoreCaseAndStatus(normalized, "PENDING").isPresent();
        if (pending) {
            return Map.of(
                    "name", normalized,
                    "available", false,
                    "exists", false,
                    "pending", true,
                    "message", "A pending credential request for '" + normalized + "' is already awaiting review."
            );
        }

        return Map.of(
                "name", normalized,
                "available", true,
                "exists", false,
                "message", "Tenant name '" + normalized + "' is available!"
        );
    }

    @Transactional
    public CredentialRequestResponse createRequest(CreateCredentialRequestDto dto) {
        String tenantName = dto.tenantName().trim().toLowerCase();
        String username = dto.username().trim();

        if (tenantRepository.existsByNameIgnoreCase(tenantName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tenant name '" + tenantName + "' is already registered. Please choose a unique tenant name.");
        }

        if (requestRepository.findByTenantNameIgnoreCaseAndStatus(tenantName, "PENDING").isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A credential request for tenant '" + tenantName + "' is already pending review.");
        }

        CredentialRequest request = CredentialRequest.builder()
                .fullName(dto.fullName().trim())
                .email(dto.email().trim().toLowerCase())
                .username(username)
                .tenantName(tenantName)
                .department(dto.department() != null ? dto.department().trim() : "General")
                .reason(dto.reason().trim())
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        CredentialRequest saved = requestRepository.save(request);

        // 1. Dispatch Email to Platform Administrator with Review & Direct Action Links
        String platformMail = platformEmailService.getPlatformMail();
        EmailNotification adminEmail = EmailNotification.builder()
                .recipientEmail(platformMail)
                .recipientName("Platform Administrator")
                .subject("[QUANTUM-LOCK] New Access Request: " + saved.getTenantName() + " (" + saved.getFullName() + ")")
                .bodyHtml(buildAdminAlertHtml(saved))
                .emailType("ADMIN_ALERT")
                .requestId(saved.getId())
                .sentAt(Instant.now())
                .build();
        emailRepository.save(adminEmail);
        platformEmailService.dispatchRealEmail(adminEmail.getRecipientEmail(), adminEmail.getSubject(), adminEmail.getBodyHtml());

        // 2. Dispatch Confirmation Email to Requester
        EmailNotification userEmail = EmailNotification.builder()
                .recipientEmail(saved.getEmail())
                .recipientName(saved.getFullName())
                .subject("[QUANTUM-LOCK] Credential Request Received for Tenant '" + saved.getTenantName() + "'")
                .bodyHtml(buildUserConfirmationHtml(saved))
                .emailType("USER_RECEIPT")
                .requestId(saved.getId())
                .sentAt(Instant.now())
                .build();
        emailRepository.save(userEmail);
        platformEmailService.dispatchRealEmail(userEmail.getRecipientEmail(), userEmail.getSubject(), userEmail.getBodyHtml());

        log.info("Credential request created: id={} tenant={} user={} email={} adminAlertTo={}",
                saved.getId(), saved.getTenantName(), saved.getUsername(), saved.getEmail(), platformMail);

        return CredentialRequestResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<CredentialRequestResponse> listAllRequests() {
        return requestRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(CredentialRequestResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CredentialRequestResponse getRequest(UUID id) {
        return requestRepository.findById(id)
                .map(CredentialRequestResponse::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException("Credential request not found: " + id));
    }

    @Transactional
    public CredentialRequestResponse reviewRequest(UUID id, ReviewCredentialRequestDto dto, AppUserDetails reviewer) {
        CredentialRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Credential request not found: " + id));

        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This request has already been reviewed (current status: " + request.getStatus() + ").");
        }

        String reviewerName = reviewer != null ? reviewer.getUsername() : "admin";

        if ("APPROVE".equalsIgnoreCase(dto.action())) {
            // 1. Ensure tenant doesn't exist
            String tenantName = request.getTenantName().toLowerCase();
            Tenant tenant = tenantRepository.findByNameIgnoreCase(tenantName)
                    .orElseGet(() -> tenantRepository.save(Tenant.builder()
                            .name(tenantName)
                            .createdAt(Instant.now())
                            .build()));

            // 2. Provision administrator user in new isolated tenant with isolated tenant context
            String password = (dto.temporaryPassword() != null && !dto.temporaryPassword().isBlank())
                    ? dto.temporaryPassword().trim()
                    : "password123";

            UUID originalTenant = TenantContext.getTenantId();
            AppUser user;
            try {
                TenantContext.setTenantId(tenant.getId());
                user = userRepository.findByUsernameAndTenantId(request.getUsername(), tenant.getId())
                        .orElseGet(() -> userRepository.save(AppUser.builder()
                                .tenantId(tenant.getId())
                                .username(request.getUsername())
                                .passwordHash(passwordEncoder.encode(password))
                                .roles(List.of("ADMIN"))
                                .department(request.getDepartment() != null ? request.getDepartment() : "Security")
                                .deviceTrustLevel("HIGH")
                                .active(true)
                                .build()));

                // 4. Record tamper-evident audit event within new tenant scope
                auditService.record(tenant.getId(),
                        reviewer != null ? reviewer.getUserId() : tenant.getId(),
                        reviewerName,
                        "AUTH", user.getId(), "TENANT_PROVISIONED", "SUCCESS",
                        "tenant=" + tenant.getName() + " adminUser=" + user.getUsername());
            } finally {
                if (originalTenant != null) {
                    TenantContext.setTenantId(originalTenant);
                } else {
                    TenantContext.clear();
                }
            }

            // 3. Mark request APPROVED
            request.setStatus("APPROVED");
            request.setReviewedAt(Instant.now());
            request.setReviewedBy(reviewerName);
            requestRepository.save(request);

            // 5. Send Approval Email to user with exact credentials
            EmailNotification approvalEmail = EmailNotification.builder()
                    .recipientEmail(request.getEmail())
                    .recipientName(request.getFullName())
                    .subject("[QUANTUM-LOCK] Credentials Approved - Your Isolated Tenant '" + tenant.getName() + "' is Ready!")
                    .bodyHtml(buildUserApprovedHtml(request, password))
                    .emailType("APPROVAL_NOTICE")
                    .requestId(request.getId())
                    .sentAt(Instant.now())
                    .build();
            emailRepository.save(approvalEmail);
            platformEmailService.dispatchRealEmail(approvalEmail.getRecipientEmail(), approvalEmail.getSubject(), approvalEmail.getBodyHtml());

            log.info("Credential request APPROVED: id={} tenant={} user={}", id, tenant.getName(), user.getUsername());
            return CredentialRequestResponse.fromEntity(request);

        } else {
            // REJECT
            String rejectionReason = (dto.rejectionReason() != null && !dto.rejectionReason().isBlank())
                    ? dto.rejectionReason().trim()
                    : "Request does not meet zero-trust verification criteria.";

            request.setStatus("REJECTED");
            request.setRejectionReason(rejectionReason);
            request.setReviewedAt(Instant.now());
            request.setReviewedBy(reviewerName);
            requestRepository.save(request);

            // Send Rejection Email to user
            EmailNotification rejectionEmail = EmailNotification.builder()
                    .recipientEmail(request.getEmail())
                    .recipientName(request.getFullName())
                    .subject("[QUANTUM-LOCK] Update on Your Access Request for '" + request.getTenantName() + "'")
                    .bodyHtml(buildUserRejectedHtml(request, rejectionReason))
                    .emailType("REJECTION_NOTICE")
                    .requestId(request.getId())
                    .sentAt(Instant.now())
                    .build();
            emailRepository.save(rejectionEmail);
            platformEmailService.dispatchRealEmail(rejectionEmail.getRecipientEmail(), rejectionEmail.getSubject(), rejectionEmail.getBodyHtml());

            log.info("Credential request REJECTED: id={} tenant={} reason={}", id, request.getTenantName(), rejectionReason);
            return CredentialRequestResponse.fromEntity(request);
        }
    }

    @Transactional(readOnly = true)
    public List<EmailNotificationResponse> listEmails() {
        return emailRepository.findAllByOrderBySentAtDesc()
                .stream()
                .map(EmailNotificationResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void markEmailRead(UUID id) {
        emailRepository.findById(id).ifPresent(e -> {
            e.setRead(true);
            emailRepository.save(e);
        });
    }

    // --- HTML Email Templates ---

    private String buildAdminAlertHtml(CredentialRequest r) {
        String baseUrl = platformEmailService.getBaseUrl();
        return """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 620px; margin: 0 auto; background: #FAF7F2; border: 1px solid #E6DCD1; border-radius: 16px; overflow: hidden; color: #2B1810;">
                <div style="background: #20130F; padding: 24px 32px; color: #FAF7F2;">
                    <div style="display: flex; align-items: center; gap: 12px;">
                        <span style="background: #C85A32; color: #FAF7F2; padding: 6px 12px; border-radius: 8px; font-weight: 800; font-size: 14px; letter-spacing: 1px;">QUANTUM-LOCK</span>
                        <span style="color: #E6DCD1; font-size: 13px; text-transform: uppercase; font-family: monospace;">Security Operations Alert</span>
                    </div>
                    <h2 style="margin: 16px 0 4px 0; font-size: 20px; font-weight: 700;">New Isolated Tenant Access Request</h2>
                    <p style="margin: 0; color: #D8CCC0; font-size: 13px;">A user has submitted an access request requiring administrator authorization.</p>
                </div>
                <div style="padding: 32px;">
                    <div style="background: #FFFFFF; border: 1px solid #E6DCD1; border-radius: 12px; padding: 20px; margin-bottom: 24px;">
                        <table style="width: 100%%; border-collapse: collapse; font-size: 14px;">
                            <tr style="border-bottom: 1px solid #F0EAE1;">
                                <td style="padding: 10px 0; color: #7A6559; font-weight: 600; width: 140px;">Requester Name:</td>
                                <td style="padding: 10px 0; font-weight: 700; color: #2B1810;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #F0EAE1;">
                                <td style="padding: 10px 0; color: #7A6559; font-weight: 600;">Email Address:</td>
                                <td style="padding: 10px 0; font-family: monospace; color: #C85A32;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #F0EAE1;">
                                <td style="padding: 10px 0; color: #7A6559; font-weight: 600;">Desired Tenant:</td>
                                <td style="padding: 10px 0; font-family: monospace; font-weight: 700; color: #2B1810; background: #FAF7F2; border-radius: 4px; display: inline-block; padding: 2px 8px; margin: 4px 0;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #F0EAE1;">
                                <td style="padding: 10px 0; color: #7A6559; font-weight: 600;">Admin Username:</td>
                                <td style="padding: 10px 0; font-family: monospace;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #F0EAE1;">
                                <td style="padding: 10px 0; color: #7A6559; font-weight: 600;">Department:</td>
                                <td style="padding: 10px 0;">%s</td>
                            </tr>
                            <tr>
                                <td style="padding: 10px 0; color: #7A6559; font-weight: 600; vertical-align: top;">Business Need:</td>
                                <td style="padding: 10px 0; line-height: 1.5; color: #4A3B32;">%s</td>
                            </tr>
                        </table>
                    </div>
                    <div style="text-align: center; margin: 28px 0 20px 0;">
                        <a href="%s/review-request/%s?action=approve" style="display: inline-block; background: #2E4B33; color: #FAF7F2; text-decoration: none; padding: 14px 26px; border-radius: 12px; font-weight: 700; font-size: 14px; box-shadow: 0 4px 12px rgba(46, 75, 51, 0.25); margin: 6px 8px;">✔ ACCEPT &amp; PROVISION</a>
                        <a href="%s/review-request/%s?action=reject" style="display: inline-block; background: #8C2D19; color: #FAF7F2; text-decoration: none; padding: 14px 26px; border-radius: 12px; font-weight: 700; font-size: 14px; box-shadow: 0 4px 12px rgba(140, 45, 25, 0.25); margin: 6px 8px;">✖ REJECT REQUEST</a>
                    </div>
                    <p style="text-align: center; margin: 0; font-size: 12px; color: #7A6559;">Review ID: <span style="font-family: monospace;">%s</span></p>
                </div>
            </div>
            """.formatted(
                r.getFullName(),
                r.getEmail(),
                r.getTenantName(),
                r.getUsername(),
                r.getDepartment() != null ? r.getDepartment() : "N/A",
                r.getReason(),
                baseUrl,
                r.getId(),
                baseUrl,
                r.getId(),
                r.getId()
        );
    }

    private String buildUserConfirmationHtml(CredentialRequest r) {
        String adminMail = platformEmailService.getPlatformMail();
        return """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 620px; margin: 0 auto; background: #FAF7F2; border: 1px solid #E6DCD1; border-radius: 16px; overflow: hidden; color: #2B1810;">
                <div style="background: #20130F; padding: 24px 32px; color: #FAF7F2;">
                    <span style="background: #C85A32; color: #FAF7F2; padding: 6px 12px; border-radius: 8px; font-weight: 800; font-size: 14px;">QUANTUM-LOCK</span>
                    <h2 style="margin: 16px 0 4px 0; font-size: 20px; font-weight: 700;">Tenant Request Received</h2>
                    <p style="margin: 0; color: #D8CCC0; font-size: 13px;">Your request is now queued for administrator review.</p>
                </div>
                <div style="padding: 32px;">
                    <p style="font-size: 15px; line-height: 1.6; margin-top: 0;">Hello <strong>%s</strong>,</p>
                    <p style="font-size: 14px; line-height: 1.6; color: #4A3B32;">
                        We have received your credential request to establish a dedicated zero-trust tenant: <strong>%s</strong>.
                        The platform security administrator (%s) has been notified to verify and approve your isolated workspace.
                    </p>
                    <div style="background: #FFFFFF; border: 1px dashed #C85A32; border-radius: 12px; padding: 16px; margin: 20px 0; font-size: 13px; color: #4A3B32;">
                        Once approved, you will receive an official approval email containing your tenant credentials and initial login link.
                    </div>
                    <p style="font-size: 12px; color: #7A6559; margin-bottom: 0;">Request Reference: <code style="background: #E6DCD1; padding: 2px 6px; border-radius: 4px;">%s</code></p>
                </div>
            </div>
            """.formatted(r.getFullName(), r.getTenantName(), adminMail, r.getId());
    }

    private String buildUserApprovedHtml(CredentialRequest r, String tempPassword) {
        String baseUrl = platformEmailService.getBaseUrl();
        return """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 620px; margin: 0 auto; background: #FAF7F2; border: 1px solid #E6DCD1; border-radius: 16px; overflow: hidden; color: #2B1810;">
                <div style="background: #2E4B33; padding: 24px 32px; color: #FAF7F2;">
                    <div style="display: flex; align-items: center; gap: 12px;">
                        <span style="background: #FFFFFF; color: #2E4B33; padding: 6px 12px; border-radius: 8px; font-weight: 800; font-size: 14px;">QUANTUM-LOCK</span>
                        <span style="color: #D8E6DB; font-size: 13px; text-transform: uppercase; font-family: monospace;">Provisioning Approved</span>
                    </div>
                    <h2 style="margin: 16px 0 4px 0; font-size: 22px; font-weight: 700;">Your Isolated Tenant is Ready!</h2>
                    <p style="margin: 0; color: #D8E6DB; font-size: 13px;">Zero-trust cryptographic isolation has been initialized for your workspace.</p>
                </div>
                <div style="padding: 32px;">
                    <p style="font-size: 15px; margin-top: 0;">Hello <strong>%s</strong>,</p>
                    <p style="font-size: 14px; line-height: 1.6; color: #4A3B32;">
                        Your request for an isolated tenant has been <strong>approved</strong> by the administrator.
                        Below are your official administrative credentials:
                    </p>
                    <div style="background: #FFFFFF; border: 2px solid #2E4B33; border-radius: 12px; padding: 20px; margin: 24px 0;">
                        <table style="width: 100%%; border-collapse: collapse; font-size: 14px;">
                            <tr style="border-bottom: 1px solid #F0EAE1;">
                                <td style="padding: 8px 0; color: #7A6559; font-weight: 600; width: 140px;">Tenant ID / Name:</td>
                                <td style="padding: 8px 0; font-family: monospace; font-weight: 700; color: #2E4B33; font-size: 15px;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #F0EAE1;">
                                <td style="padding: 8px 0; color: #7A6559; font-weight: 600;">Username:</td>
                                <td style="padding: 8px 0; font-family: monospace; font-weight: 700; color: #2B1810;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #F0EAE1;">
                                <td style="padding: 8px 0; color: #7A6559; font-weight: 600;">Assigned Role:</td>
                                <td style="padding: 8px 0;"><span style="background: #C85A32; color: #FAF7F2; padding: 2px 8px; border-radius: 4px; font-weight: 700; font-size: 11px;">ADMIN</span></td>
                            </tr>
                            <tr>
                                <td style="padding: 8px 0; color: #7A6559; font-weight: 600;">Temporary Password:</td>
                                <td style="padding: 8px 0; font-family: monospace; font-weight: 700; background: #FAF7F2; border-radius: 4px; color: #C85A32; font-size: 15px; display: inline-block; padding: 4px 10px; margin: 4px 0;">%s</td>
                            </tr>
                        </table>
                    </div>
                    <div style="text-align: center; margin: 28px 0 16px 0;">
                        <a href="%s/login?tenant=%s&username=%s" style="display: inline-block; background: #2E4B33; color: #FAF7F2; text-decoration: none; padding: 14px 32px; border-radius: 12px; font-weight: 700; font-size: 14px; box-shadow: 0 4px 12px rgba(46, 75, 51, 0.25);">Log In to Your Tenant</a>
                    </div>
                    <p style="text-align: center; font-size: 12px; color: #7A6559; margin-bottom: 0;">
                        Cryptographic Invariant I2 enforced: MinIO storage and AES-256-GCM keys are strictly partitioned.
                    </p>
                </div>
            </div>
            """.formatted(
                r.getFullName(),
                r.getTenantName(),
                r.getUsername(),
                tempPassword,
                baseUrl,
                r.getTenantName(),
                r.getUsername()
        );
    }

    private String buildUserRejectedHtml(CredentialRequest r, String reason) {
        String adminMail = platformEmailService.getPlatformMail();
        return """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 620px; margin: 0 auto; background: #FAF7F2; border: 1px solid #E6DCD1; border-radius: 16px; overflow: hidden; color: #2B1810;">
                <div style="background: #8C2D19; padding: 24px 32px; color: #FAF7F2;">
                    <span style="background: #FFFFFF; color: #8C2D19; padding: 6px 12px; border-radius: 8px; font-weight: 800; font-size: 14px;">QUANTUM-LOCK</span>
                    <h2 style="margin: 16px 0 4px 0; font-size: 20px; font-weight: 700;">Access Request Status Update</h2>
                </div>
                <div style="padding: 32px;">
                    <p style="font-size: 15px; margin-top: 0;">Hello <strong>%s</strong>,</p>
                    <p style="font-size: 14px; line-height: 1.6; color: #4A3B32;">
                        Thank you for your interest in QUANTUM-LOCK. After administrative review, your credential request for tenant <strong>%s</strong> could not be approved at this time.
                    </p>
                    <div style="background: #FFFFFF; border-left: 4px solid #8C2D19; padding: 14px 18px; border-radius: 0 8px 8px 0; margin: 20px 0; font-size: 13px; color: #4A3B32;">
                        <strong>Reason provided:</strong> %s
                    </div>
                    <p style="font-size: 13px; color: #7A6559; margin-bottom: 0;">
                        If you believe this was in error, please contact security operations at <a href="mailto:%s" style="color: #C85A32;">%s</a>.
                    </p>
                </div>
            </div>
            """.formatted(r.getFullName(), r.getTenantName(), reason, adminMail, adminMail);
    }
}
