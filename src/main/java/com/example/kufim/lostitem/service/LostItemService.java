package com.example.kufim.lostitem.service;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.audit.entity.AuditLog;
import com.example.kufim.audit.repository.AuditLogRepository;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.common.util.PersonalDataMasker;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.repository.FestivalRepository;
import com.example.kufim.lostitem.dto.LostItemCreateRequest;
import com.example.kufim.lostitem.dto.LostItemReturnRequest;
import com.example.kufim.lostitem.dto.LostItemUpdateRequest;
import com.example.kufim.lostitem.entity.LostItem;
import com.example.kufim.lostitem.entity.LostItemCategory;
import com.example.kufim.lostitem.entity.LostItemStatus;
import com.example.kufim.lostitem.repository.LostItemRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 분실물센터. 상황실이 습득물을 등록하고 방문자가 목록·상세를 조회한 뒤 직접 찾아갑니다.
 *
 * 개인정보를 다루므로 두 가지를 지킵니다.
 * 신분증·지갑은 요청 값과 무관하게 사진을 비공개로 저장하고,
 * 인계가 끝난 물건은 방문자 쪽에서 상세·사진을 아예 열 수 없게 막습니다.
 */
@Service
@RequiredArgsConstructor
public class LostItemService {
    private static final String DEFAULT_STORAGE_PLACE = "상황실";
    /** 업로드만 해 두고 등록하지 않은 사진을 정리하기까지의 유예. */
    static final Duration ORPHAN_IMAGE_TTL = Duration.ofHours(24);

    private final LostItemRepository items;
    private final FestivalRepository festivals;
    private final AuditLogRepository auditLogs;
    private final LostItemImageStore images;

    // ---------------------------------------------------------------- 방문자

    /** status를 생략하면 보관 중만, ALL이면 전체를 돌려줍니다. */
    @Transactional(readOnly = true)
    public Map<String, Object> publicList(LostItemCategory category, String status, String q, int page, int size) {
        LostItemStatus parsed = parseVisitorStatus(status);
        var found = items.findAll(filter(activeFestival().getId(), parsed, category, q), paging(page, size));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", found.getContent().stream().map(this::publicView).toList());
        result.put("page", found.getNumber());
        result.put("size", found.getSize());
        result.put("totalElements", found.getTotalElements());
        result.put("totalPages", found.getTotalPages());
        result.put("serverTime", ApiClock.offset(ApiClock.now()));
        return result;
    }

    /**
     * 방문자 상세. 없는 건, 삭제된 건, 인계가 끝난 건을 모두 같은 404로 돌려줍니다.
     * 코드를 나누면 "그 물건은 이미 누가 찾아갔다"는 사실이 밖으로 새기 때문입니다.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> publicOne(long lostItemId) {
        return publicView(requireVisible(lostItemId));
    }

    /** 방문자용 사진. 공개 설정이고 보관 중일 때만 내려줍니다. */
    @Transactional(readOnly = true)
    public byte[] publicImage(long lostItemId, boolean thumbnail) {
        LostItem item = requireVisible(lostItemId);
        if (!item.isImagePublic()) throw notFound();
        byte[] bytes = images.read(item.getImageKey(), thumbnail);
        if (bytes == null) throw notFound();
        return bytes;
    }

    private LostItem requireVisible(long lostItemId) {
        LostItem item = items.findById(lostItemId).orElseThrow(this::notFound);
        if (item.deleted() || item.getStatus() == LostItemStatus.RETURNED) throw notFound();
        return item;
    }

    // ---------------------------------------------------------------- 관리자

    @Transactional(readOnly = true)
    public Map<String, Object> adminList(LostItemCategory category, LostItemStatus status, String q, int page, int size) {
        Festival festival = activeFestival();
        var found = items.findAll(filter(festival.getId(), status, category, q), paging(page, size));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stored", 0L);
        summary.put("returned", 0L);
        long total = 0;
        for (Object[] row : items.countByStatus(festival.getId())) {
            long count = (long) row[1];
            summary.put(row[0] == LostItemStatus.STORED ? "stored" : "returned", count);
            total += count;
        }
        summary.put("total", total);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("content", found.getContent().stream().map(item -> adminView(item, false)).toList());
        result.put("page", found.getNumber());
        result.put("size", found.getSize());
        result.put("totalElements", found.getTotalElements());
        result.put("totalPages", found.getTotalPages());
        result.put("serverTime", ApiClock.offset(ApiClock.now()));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminOne(long lostItemId) {
        return adminView(findAlive(lostItemId), true);
    }

    /** 관리자용 사진. 비공개 사진과 인계가 끝난 물건도 볼 수 있습니다. */
    @Transactional(readOnly = true)
    public byte[] adminImage(long lostItemId, boolean thumbnail) {
        byte[] bytes = images.read(findAlive(lostItemId).getImageKey(), thumbnail);
        if (bytes == null) throw notFound();
        return bytes;
    }

    /** 사진을 먼저 올려 키를 받습니다. 등록에 쓰이지 않으면 하루 뒤 정리됩니다. */
    public Map<String, Object> uploadImage(MultipartFile file) {
        return Map.of("imageKey", images.save(file));
    }

    @Transactional
    public Map<String, Object> register(LostItemCreateRequest input, AdminAccount actor) {
        LocalDateTime foundAt = toLocal(input.foundAt());
        if (foundAt.isAfter(ApiClock.now()))
            throw new ContractException(HttpStatus.BAD_REQUEST, "C001", "습득 일시는 현재 시각 이전이어야 합니다.");
        String imageKey = requireUploaded(input.imageKey());
        // 신분증·지갑은 사진으로 내용이 드러날 수 있어 요청 값과 무관하게 비공개입니다.
        boolean imagePublic = !input.category().sensitive() && !Boolean.FALSE.equals(input.imagePublic());

        LostItem item = items.save(LostItem.builder()
                .festival(activeFestival())
                .category(input.category())
                .name(input.name().strip())
                .foundPlace(input.foundPlace().strip())
                .foundAt(foundAt)
                .storagePlace(blankTo(input.storagePlace(), DEFAULT_STORAGE_PLACE))
                .description(stripOrNull(input.description()))
                .imageKey(imageKey)
                .imagePublic(imagePublic)
                .status(LostItemStatus.STORED)
                .registeredBy(actor)
                .build());
        audit(actor, "LOST_ITEM_CREATE", item, "registered");
        return adminView(item, true);
    }

    /** 수정. 인계가 끝난 뒤에도 오타를 고칠 수 있게 열어 둡니다. */
    @Transactional
    public Map<String, Object> edit(long lostItemId, LostItemUpdateRequest input, AdminAccount actor) {
        LostItem item = findAlive(lostItemId);
        LocalDateTime foundAt = input.getFoundAt() == null ? null : toLocal(input.getFoundAt());
        if (foundAt != null && foundAt.isAfter(ApiClock.now()))
            throw new ContractException(HttpStatus.BAD_REQUEST, "C001", "습득 일시는 현재 시각 이전이어야 합니다.");

        item.edit(input.getCategory(),
                stripOrNull(input.getName()),
                stripOrNull(input.getFoundPlace()),
                foundAt,
                stripOrNull(input.getStoragePlace()),
                input.descriptionPresent() ? stripOrNull(input.getDescription()) : item.getDescription(),
                input.getImagePublic());

        if (input.imageKeyPresent()) {
            String previous = item.getImageKey();
            item.changeImage(requireUploaded(input.getImageKey()));
            // 새 사진이 자리를 잡은 뒤에 예전 파일을 지웁니다.
            if (previous != null && !previous.equals(item.getImageKey())) images.delete(previous);
        }
        audit(actor, "LOST_ITEM_UPDATE", item, "updated");
        return adminView(item, true);
    }

    @Transactional
    public Map<String, Object> markReturned(long lostItemId, LostItemReturnRequest input, AdminAccount actor) {
        LostItem item = lock(lostItemId);
        if (item.getStatus() == LostItemStatus.RETURNED)
            throw new ContractException(HttpStatus.CONFLICT, "L002", "이미 처리된 습득물입니다.");
        item.markReturned(input.receiverName().strip(), blankToNull(input.receiverPhone()),
                input.verifiedBy(), stripOrNull(input.returnNote()), actor, ApiClock.now());
        audit(actor, "LOST_ITEM_RETURN", item, "returned to " + item.getReceiverName());
        return adminView(item, true);
    }

    @Transactional
    public Map<String, Object> reopen(long lostItemId, AdminAccount actor) {
        LostItem item = lock(lostItemId);
        if (item.getStatus() == LostItemStatus.STORED)
            throw new ContractException(HttpStatus.CONFLICT, "L002", "이미 처리된 습득물입니다.");
        item.reopen();
        audit(actor, "LOST_ITEM_REOPEN", item, "return canceled");
        return adminView(item, true);
    }

    /** 수령인 연락처는 조회만으로도 이력에 남깁니다. */
    @Transactional
    public Map<String, Object> contact(long lostItemId, AdminAccount actor) {
        LostItem item = findAlive(lostItemId);
        if (item.getReceiverPhone() == null)
            throw new ContractException(HttpStatus.NOT_FOUND, "L001", "습득물을 찾을 수 없습니다.");
        audit(actor, "LOST_ITEM_CONTACT_VIEW", item, "receiver phone viewed");
        return Map.of("lostItemId", item.getId(), "phoneNumber", item.getReceiverPhone());
    }

    /** 기록은 남기고 목록에서만 감춥니다. 사진 파일은 바로 지웁니다. */
    @Transactional
    public void delete(long lostItemId, AdminAccount actor) {
        LostItem item = findAlive(lostItemId);
        String imageKey = item.getImageKey();
        item.softDelete(ApiClock.now());
        audit(actor, "LOST_ITEM_DELETE", item, "soft deleted");
        images.delete(imageKey);
    }

    // ---------------------------------------------------------------- 정리·파기

    /** 등록에 쓰이지 않은 사진 정리. 스케줄러가 부릅니다. */
    @Transactional(readOnly = true)
    public int cleanUpOrphanImages() {
        return images.deleteOrphans(items.findUsedImageKeys(), ORPHAN_IMAGE_TTL);
    }

    /** 개인정보 파기. 수령인 이름은 마스킹하고 연락처와 사진은 지웁니다. */
    @Transactional
    public int purgePersonalData(LocalDateTime before) {
        List<LostItem> targets = items.findPurgeTargets(before);
        for (LostItem item : targets) {
            String imageKey = item.getImageKey();
            item.purgePersonalData();
            item.changeImage(null);
            images.delete(imageKey);
        }
        return targets.size();
    }

    // ---------------------------------------------------------------- 공통 규칙

    /** 목록 조건. 삭제된 건은 방문자·관리자 모두에게서 빠집니다. */
    private Specification<LostItem> filter(Long festivalId, LostItemStatus status, LostItemCategory category, String q) {
        return (root, query, builder) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(builder.equal(root.get("festival").get("id"), festivalId));
            where.add(builder.isNull(root.get("deletedAt")));
            if (status != null) where.add(builder.equal(root.get("status"), status));
            if (category != null) where.add(builder.equal(root.get("category"), category));
            String keyword = normalizeKeyword(q);
            if (keyword != null) {
                String like = "%" + keyword + "%";
                where.add(builder.or(
                        builder.like(builder.lower(root.get("name")), like),
                        builder.like(builder.lower(root.get("foundPlace")), like)));
            }
            return builder.and(where.toArray(new Predicate[0]));
        };
    }

    private PageRequest paging(int page, int size) {
        int capped = size < 1 ? 20 : Math.min(size, 50);
        return PageRequest.of(Math.max(page, 0), capped,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    /** 검색어는 2자 미만이면 조건에서 뺍니다. 한 글자로는 거의 전부가 걸리기 때문입니다. */
    private String normalizeKeyword(String q) {
        if (q == null) return null;
        String trimmed = q.strip().toLowerCase(Locale.ROOT);
        if (trimmed.length() < 2) return null;
        return trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed;
    }

    private LostItemStatus parseVisitorStatus(String status) {
        if (status == null || status.isBlank()) return LostItemStatus.STORED;
        if ("ALL".equalsIgnoreCase(status)) return null;
        try {
            return LostItemStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ContractException(HttpStatus.BAD_REQUEST, "C001", "입력값을 확인해 주세요.");
        }
    }

    /** 사진 키는 업로드 API가 만든 것만 받습니다. */
    private String requireUploaded(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) return null;
        if (images.read(imageKey, false) == null)
            throw new ContractException(HttpStatus.BAD_REQUEST, "L003", "지원하지 않는 이미지입니다.");
        return imageKey;
    }

    private LostItem findAlive(long lostItemId) {
        LostItem item = items.findById(lostItemId).orElseThrow(this::notFound);
        if (item.deleted()) throw notFound();
        return item;
    }

    private LostItem lock(long lostItemId) {
        LostItem item = items.findByIdForUpdate(lostItemId).orElseThrow(this::notFound);
        if (item.deleted()) throw notFound();
        return item;
    }

    private ContractException notFound() {
        return new ContractException(HttpStatus.NOT_FOUND, "L001", "습득물을 찾을 수 없습니다.");
    }

    private Festival activeFestival() {
        return festivals.findAll().stream().max(Comparator.comparingLong(Festival::getId))
                .orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND, "C005", "진행 중인 축제가 없습니다."));
    }

    private void audit(AdminAccount actor, String action, LostItem item, String detail) {
        auditLogs.save(AuditLog.builder()
                .adminAccount(actor)
                .action(action)
                .targetType("LOST_ITEM")
                .targetId(String.valueOf(item.getId()))
                .detail(detail)
                .occurredAt(ApiClock.now())
                .build());
    }

    private LocalDateTime toLocal(OffsetDateTime value) {
        return value.atZoneSameInstant(java.time.ZoneOffset.ofHours(9)).toLocalDateTime();
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String stripOrNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    // ---------------------------------------------------------------- 응답

    /**
     * 방문자 응답. 인계가 끝난 건은 목록에만 남기고 상세 내용을 비웁니다.
     * 프런트가 필드를 찾지 못해 깨지지 않도록 키는 두고 값만 null로 둡니다.
     */
    private Map<String, Object> publicView(LostItem item) {
        boolean closed = item.getStatus() == LostItemStatus.RETURNED;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lostItemId", item.getId());
        result.put("category", item.getCategory());
        result.put("name", item.getName());
        result.put("foundPlace", closed ? null : item.getFoundPlace());
        result.put("foundAt", closed ? null : ApiClock.offset(item.getFoundAt()));
        result.put("storagePlace", closed ? null : item.getStoragePlace());
        result.put("description", closed ? null : item.getDescription());
        result.put("status", item.getStatus());
        boolean showImage = !closed && item.isImagePublic() && item.getImageKey() != null;
        result.put("imageUrl", showImage ? imageUrl(item.getId(), false, false) : null);
        result.put("thumbnailUrl", showImage ? imageUrl(item.getId(), true, false) : null);
        result.put("createdAt", ApiClock.offset(item.getCreatedAt()));
        result.put("elapsedMinutes", closed ? null : elapsedMinutes(item));
        return result;
    }

    /** 관리자 응답. 사진은 공개 설정과 무관하게 항상 내려줍니다. */
    private Map<String, Object> adminView(LostItem item, boolean detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lostItemId", item.getId());
        result.put("category", item.getCategory());
        result.put("name", item.getName());
        result.put("foundPlace", item.getFoundPlace());
        result.put("foundAt", ApiClock.offset(item.getFoundAt()));
        result.put("storagePlace", item.getStoragePlace());
        result.put("description", item.getDescription());
        result.put("status", item.getStatus());
        result.put("imageUrl", item.getImageKey() == null ? null : imageUrl(item.getId(), false, true));
        result.put("thumbnailUrl", item.getImageKey() == null ? null : imageUrl(item.getId(), true, true));
        result.put("createdAt", ApiClock.offset(item.getCreatedAt()));
        result.put("elapsedMinutes", elapsedMinutes(item));
        result.put("imagePublic", item.isImagePublic());
        result.put("registeredBy", accountView(item.getRegisteredBy()));
        result.put("returnedAt", ApiClock.offset(item.getReturnedAt()));
        result.put("returnedBy", accountView(item.getReturnedBy()));
        // 목록에서는 수령인 이름도 가립니다. 상세에서만 전체를 봅니다.
        result.put("receiverName", detail ? item.getReceiverName() : maskName(item.getReceiverName()));
        result.put("receiverPhoneMasked", maskPhone(item.getReceiverPhone()));
        result.put("verifiedBy", item.getVerifiedBy());
        result.put("returnNote", item.getReturnNote());
        result.put("linkedReport", null);
        return result;
    }

    private long elapsedMinutes(LostItem item) {
        return Duration.between(item.getCreatedAt(), ApiClock.now()).toMinutes();
    }

    private String imageUrl(long lostItemId, boolean thumbnail, boolean admin) {
        return "/api/v1%s/lost-items/%d/image?size=%s".formatted(admin ? "/admin" : "", lostItemId,
                thumbnail ? "thumb" : "full");
    }

    /** 프런트가 "010-****-1234" 형태를 기대하므로 하이픈을 넣어 돌려줍니다. */
    private String maskPhone(String value) {
        if (value == null) return null;
        String masked = PersonalDataMasker.phoneNumber(value);
        return masked.length() == 11 ? masked.substring(0, 3) + "-" + masked.substring(3, 7) + "-" + masked.substring(7)
                : masked;
    }

    private String maskName(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.length() == 1) return value;
        if (value.length() == 2) return value.charAt(0) + "*";
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }

    private Map<String, Object> accountView(AdminAccount account) {
        if (account == null) return null;
        return Map.of("accountId", account.getId(), "name", account.getName());
    }
}
