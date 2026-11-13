package com.example.kufim.report.service;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.audit.entity.AuditLog;
import com.example.kufim.audit.repository.AuditLogRepository;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.waiting.service.ClientToken;
import com.example.kufim.common.util.PersonalDataMasker;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.repository.FestivalRepository;
import com.example.kufim.notification.entity.Notification;
import com.example.kufim.notification.entity.NotificationType;
import com.example.kufim.notification.repository.NotificationRepository;
import com.example.kufim.report.dto.ReportCompleteRequest;
import com.example.kufim.report.dto.ReportCreateRequest;
import com.example.kufim.report.dto.ReportNoteRequest;
import com.example.kufim.report.entity.Report;
import com.example.kufim.report.entity.ReportCategory;
import com.example.kufim.report.entity.ReportStatus;
import com.example.kufim.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 긴급 신고의 등록·조회와 처리 상태 전이를 담당합니다.
 * 상태는 관리자가 직접 고르지 않고 동작(상세 열기·메모 저장·완료 처리)에 따라 바뀌며,
 * 한 번 바뀌면 되돌릴 수 없습니다. 상태가 실제로 바뀐 경우에만 이력·알림·문자가 나갑니다.
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    private static final String PURGED_NUMBER = "00000000000";

    private final ReportRepository reports;
    private final FestivalRepository festivals;
    private final NotificationRepository notifications;
    private final AuditLogRepository auditLogs;
    private final ReportSmsSender sms;

    // ---------------------------------------------------------------- 방문자

    @Transactional
    public Map<String, Object> register(String rawToken, ReportCreateRequest input) {
        String token = ClientToken.require(rawToken);
        if (!Boolean.TRUE.equals(input.privacyConsent()))
            throw new ContractException(HttpStatus.BAD_REQUEST, "C002", "개인정보 동의가 필요합니다.");
        Report report = reports.save(Report.builder()
                .festival(activeFestival())
                .clientToken(token)
                .category(input.category())
                .title(input.title().strip())
                .reporterName(input.reporterName().strip())
                .phoneNumber(input.phoneNumber())
                .place(input.place().strip())
                .content(input.content().strip())
                .status(ReportStatus.SUBMITTED)
                .build());
        return view(report);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(String rawToken) {
        String token = ClientToken.require(rawToken);
        return reports.findByClientTokenOrderByCreatedAtDescIdDesc(token).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> owned(long reportId, String rawToken) {
        return view(requireOwned(reportId, rawToken));
    }

    /** 아직 접수되지 않은 자기 신고만 취소할 수 있습니다. 취소는 알림·문자를 보내지 않습니다. */
    @Transactional
    public void cancelOwned(long reportId, String rawToken) {
        Report report = requireOwned(reportId, rawToken);
        if (report.getStatus() != ReportStatus.SUBMITTED)
            throw new ContractException(HttpStatus.CONFLICT, "R005", "이미 접수된 신고는 취소할 수 없습니다.");
        report.moveTo(ReportStatus.CANCELED, null, ApiClock.now());
    }

    private Report requireOwned(long reportId, String rawToken) {
        String token = ClientToken.require(rawToken);
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "R001", "신고를 찾을 수 없습니다."));
        if (!report.getClientToken().equals(token))
            throw new ContractException(HttpStatus.FORBIDDEN, "R002", "신고 소유권이 없습니다.");
        return report;
    }

    // ---------------------------------------------------------------- 관리자

    @Transactional(readOnly = true)
    public Map<String, Object> adminList(ReportStatus status, ReportCategory category, int page, int size) {
        Festival festival = activeFestival();
        var pageable = PageRequest.of(Math.max(page, 0), size < 1 ? 20 : Math.min(size, 100));
        // status · category 는 각각 생략 가능하므로 네 조합을 그대로 나눕니다.
        var found = status == null
                ? (category == null
                        ? reports.findByFestivalIdOrderByCreatedAtDescIdDesc(festival.getId(), pageable)
                        : reports.findByFestivalIdAndCategoryOrderByCreatedAtDescIdDesc(
                                festival.getId(), category, pageable))
                : (category == null
                        ? reports.findByFestivalIdAndStatusOrderByCreatedAtDescIdDesc(
                                festival.getId(), status, pageable)
                        : reports.findByFestivalIdAndStatusAndCategoryOrderByCreatedAtDescIdDesc(
                                festival.getId(), status, category, pageable));

        Map<String, Object> summary = new LinkedHashMap<>();
        for (String key : List.of("submitted", "received", "inProgress", "completed", "canceled"))
            summary.put(key, 0L);
        for (Object[] row : reports.countByStatus(festival.getId()))
            summary.put(summaryKey((ReportStatus) row[0]), row[1]);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("content", found.getContent().stream().map(this::adminView).toList());
        result.put("page", found.getNumber());
        result.put("size", found.getSize());
        result.put("totalElements", found.getTotalElements());
        result.put("totalPages", found.getTotalPages());
        result.put("serverTime", ApiClock.offset(ApiClock.now()));
        return result;
    }

    private String summaryKey(ReportStatus status) {
        return switch (status) {
            case SUBMITTED -> "submitted";
            case RECEIVED -> "received";
            case IN_PROGRESS -> "inProgress";
            case COMPLETED -> "completed";
            case CANCELED -> "canceled";
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminOne(long reportId) {
        return adminView(find(reportId));
    }

    /**
     * 관리자가 상세를 열 때 호출합니다. 신청 상태면 접수로 넘기고, 이미 진행된 건은 그대로 둡니다.
     * 두 관리자가 동시에 열어도 알림이 한 번만 나가도록 행을 잠그고 상태를 다시 확인합니다.
     */
    @Transactional
    public Map<String, Object> receive(long reportId, AdminAccount actor) {
        Report report = lock(reportId);
        if (report.getStatus() != ReportStatus.SUBMITTED) return unchanged(report);
        return applyTransition(report, ReportStatus.RECEIVED, actor, true);
    }

    /**
     * 처리 메모를 저장합니다. 접수 상태였다면 처리 중으로 넘어가고,
     * 이미 처리 중이면 상태는 그대로 두고 앱 알림만 보냅니다.
     */
    @Transactional
    public Map<String, Object> saveMemo(long reportId, ReportNoteRequest input, AdminAccount actor) {
        Report report = lock(reportId);
        requireOpen(report);
        LocalDateTime now = ApiClock.now();
        report.writeNote(input.adminNote().strip(), actor, now);
        audit(actor, "REPORT_NOTE_UPDATE", report, "note updated");

        if (report.getStatus() == ReportStatus.IN_PROGRESS) {
            notify(report, "[신고] %s 처리 안내가 업데이트되었습니다".formatted(report.getTitle()), report.getAdminNote());
            Map<String, Object> result = adminView(report);
            result.put("statusChanged", false);
            result.put("smsStatus", "SKIPPED");
            return result;
        }
        // 신청 상태에서 바로 저장한 경우에도 접수 시각은 남기되, 알림과 문자는 처리 중 한 번만 보냅니다.
        if (report.getStatus() == ReportStatus.SUBMITTED) report.moveTo(ReportStatus.RECEIVED, actor, now);
        return applyTransition(report, ReportStatus.IN_PROGRESS, actor, true);
    }

    /** 완료 처리. 접수를 거치지 않았다면 접수 시각만 남기고 바로 완료합니다. */
    @Transactional
    public Map<String, Object> complete(long reportId, ReportCompleteRequest input, AdminAccount actor) {
        Report report = lock(reportId);
        requireOpen(report);
        LocalDateTime now = ApiClock.now();
        if (input != null && input.adminNote() != null && !input.adminNote().isBlank()) {
            report.writeNote(input.adminNote().strip(), actor, now);
            audit(actor, "REPORT_NOTE_UPDATE", report, "note updated with completion");
        }
        if (report.getStatus() == ReportStatus.SUBMITTED) report.moveTo(ReportStatus.RECEIVED, actor, now);
        return applyTransition(report, ReportStatus.COMPLETED, actor, true);
    }

    /** 전체 전화번호를 보여 주는 것은 조회만으로도 이력에 남깁니다. */
    @Transactional
    public Map<String, Object> contact(long reportId, AdminAccount actor) {
        Report report = find(reportId);
        if (PURGED_NUMBER.equals(report.getPhoneNumber()))
            throw new ContractException(HttpStatus.CONFLICT, "R004", "파기된 연락처입니다.");
        audit(actor, "REPORT_CONTACT_VIEW", report, "phone number viewed");
        return Map.of("reportId", report.getId(), "phoneNumber", report.getPhoneNumber());
    }

    // ---------------------------------------------------------------- 공통 규칙

    /** 상태를 옮기고 이력·알림·문자를 한 번에 처리합니다. */
    private Map<String, Object> applyTransition(Report report, ReportStatus next, AdminAccount actor, boolean withSms) {
        ReportStatus before = report.getStatus();
        report.moveTo(next, actor, ApiClock.now());
        audit(actor, "REPORT_STATUS_CHANGE", report, "%s -> %s".formatted(before, next));
        notify(report, "[신고] %s 상태가 %s(으)로 변경되었습니다".formatted(report.getTitle(), next.label()),
                report.getAdminNote() == null || report.getAdminNote().isBlank()
                        ? "신고가 %s 상태로 변경되었습니다.".formatted(next.label())
                        : report.getAdminNote());
        String smsStatus = withSms ? sms.send(report, next, actor) : "SKIPPED";
        Map<String, Object> result = adminView(report);
        result.put("statusChanged", true);
        result.put("smsStatus", smsStatus);
        return result;
    }

    private Map<String, Object> unchanged(Report report) {
        Map<String, Object> result = adminView(report);
        result.put("statusChanged", false);
        result.put("smsStatus", "SKIPPED");
        return result;
    }

    private void requireOpen(Report report) {
        if (report.getStatus().closed())
            throw new ContractException(HttpStatus.CONFLICT, "R003", "이미 종결된 신고입니다.");
    }

    private Report find(long reportId) {
        return reports.findById(reportId)
                .orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "R001", "신고를 찾을 수 없습니다."));
    }

    private Report lock(long reportId) {
        return reports.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "R001", "신고를 찾을 수 없습니다."));
    }

    private void notify(Report report, String title, String body) {
        notifications.save(Notification.builder()
                .festival(report.getFestival())
                .type(NotificationType.REPORT_STATUS)
                .report(report)
                .recipientClientToken(report.getClientToken())
                .title(title)
                .body(body)
                .build());
    }

    private void audit(AdminAccount actor, String action, Report report, String detail) {
        auditLogs.save(AuditLog.builder()
                .adminAccount(actor)
                .action(action)
                .targetType("REPORT")
                .targetId(String.valueOf(report.getId()))
                .detail(detail)
                .occurredAt(ApiClock.now())
                .build());
    }

    private Festival activeFestival() {
        return festivals.findAll().stream().max(Comparator.comparingLong(Festival::getId))
                .orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "C005", "진행 중인 축제가 없습니다."));
    }

    // ---------------------------------------------------------------- 응답

    /** 방문자 응답. 전화번호는 포함하지 않습니다. */
    private Map<String, Object> view(Report r) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", r.getId());
        result.put("category", r.getCategory());
        result.put("title", r.getTitle());
        result.put("reporterName", r.getReporterName());
        result.put("place", r.getPlace());
        result.put("content", r.getContent());
        result.put("status", r.getStatus());
        result.put("adminNote", r.getAdminNote());
        result.put("adminNoteUpdatedAt", ApiClock.offset(r.getAdminNoteUpdatedAt()));
        result.put("createdAt", ApiClock.offset(r.getCreatedAt()));
        result.put("receivedAt", ApiClock.offset(r.getReceivedAt()));
        result.put("inProgressAt", ApiClock.offset(r.getInProgressAt()));
        result.put("statusChangedAt", ApiClock.offset(r.getStatusChangedAt()));
        result.put("completedAt", ApiClock.offset(r.getCompletedAt()));
        return result;
    }

    /** 관리자 응답. 방문자 응답에 마스킹된 번호와 담당자, 경과 시간을 더합니다. */
    private Map<String, Object> adminView(Report r) {
        Map<String, Object> result = view(r);
        result.put("phoneNumberMasked", PersonalDataMasker.phoneNumber(r.getPhoneNumber()));
        result.put("statusChangedBy", accountView(r.getStatusChangedBy()));
        result.put("adminNoteUpdatedBy", accountView(r.getAdminNoteUpdatedBy()));
        result.put("elapsedMinutes", java.time.Duration.between(r.getCreatedAt(), ApiClock.now()).toMinutes());
        return result;
    }

    private Map<String, Object> accountView(AdminAccount account) {
        if (account == null) return null;
        return Map.of("accountId", account.getId(), "name", account.getName());
    }
}
