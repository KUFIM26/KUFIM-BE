package com.example.kufim.lostitem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 업로드만 해 두고 등록하지 않은 사진을 정리합니다.
 * 등록 화면에서 사진만 올리고 저장하지 않은 경우가 쌓이면 디스크를 차지하기 때문입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LostItemImageCleanupJob {
    private final LostItemService service;

    @Scheduled(initialDelayString = "${app.lost-item.image-cleanup-initial-delay-ms:600000}",
            fixedDelayString = "${app.lost-item.image-cleanup-interval-ms:3600000}")
    public void run() {
        int removed = service.cleanUpOrphanImages();
        if (removed > 0) log.info("등록되지 않은 습득물 사진 {}개를 정리했습니다.", removed);
    }
}
