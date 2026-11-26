package com.example.kufim.translate.service;

import com.example.kufim.booth.repository.BoothRepository;
import com.example.kufim.facility.repository.FacilityRepository;
import com.example.kufim.festival.repository.FestivalRepository;
import com.example.kufim.map.repository.MapFloorplanRepository;
import com.example.kufim.notice.repository.NoticeRepository;
import com.example.kufim.performance.repository.PerformanceRepository;
import com.example.kufim.stage.repository.StageRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미 등록된 내용의 영어 칸을 한 번에 채웁니다.
 *
 * <p>영어가 비어 있는 칸만 건드립니다. 운영진이 직접 고쳐 둔 값은 그대로 둡니다.
 * 한 칸이 실패해도 나머지는 계속 채우고, 실패 건수만 세어 돌려줍니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnglishBackfillService {
    /** DeepL은 한 번에 여러 문장을 받습니다. 호출 수를 줄이려고 묶어 보냅니다. */
    private static final int BATCH = 20;

    private final Translator translator;
    private final FestivalRepository festivals;
    private final StageRepository stages;
    private final PerformanceRepository performances;
    private final BoothRepository booths;
    private final FacilityRepository facilities;
    private final NoticeRepository notices;
    private final MapFloorplanRepository floorplans;

    /** 번역해 채울 칸 하나. */
    private record Slot(String group, String korean, int max, Consumer<String> apply) {}

    @Transactional
    public Map<String, Object> run() {
        if (!translator.configured()) throw new TranslationUnavailableException();

        List<Slot> slots = new ArrayList<>();
        for (var f : festivals.findAll())
            add(slots, "festival", f.getName(), f.getNameEn(), 40, f::applyNameEn);
        for (var s : stages.findAll())
            add(slots, "stage", s.getName(), s.getNameEn(), 40, s::applyNameEn);
        for (var p : performances.findAllByDeletedFalseOrderByStartAtAsc()) {
            add(slots, "performance", p.getTitle(), p.getTitleEn(), 40, p::applyTitleEn);
            add(slots, "performance", p.getDescription(), p.getDescriptionEn(), 1000, p::applyDescriptionEn);
        }
        for (var b : booths.findAllByDeletedFalseOrderByIdAsc()) {
            add(slots, "booth", b.getName(), b.getNameEn(), 40, b::applyNameEn);
            add(slots, "booth", b.getPlace(), b.getPlaceEn(), 60, b::applyPlaceEn);
            add(slots, "booth", b.getDescription(), b.getDescriptionEn(), 1000, b::applyDescriptionEn);
        }
        for (var f : facilities.findAllByDeletedFalseOrderByIdAsc()) {
            add(slots, "facility", f.getName(), f.getNameEn(), 40, f::applyNameEn);
            add(slots, "facility", f.getPlace(), f.getPlaceEn(), 60, f::applyPlaceEn);
        }
        for (var n : notices.findAll()) {
            if (n.isDeleted()) continue;
            add(slots, "notice", n.getTitle(), n.getTitleEn(), 60, n::applyTitleEn);
            add(slots, "notice", n.getContent(), n.getContentEn(), 2000, n::applyContentEn);
        }

        Map<String, Integer> filled = new LinkedHashMap<>();
        int skipped = 0;
        for (int from = 0; from < slots.size(); from += BATCH) {
            var chunk = slots.subList(from, Math.min(from + BATCH, slots.size()));
            List<String> translated;
            try {
                translated = translator.toEnglish(chunk.stream().map(Slot::korean).toList());
            } catch (RuntimeException e) {
                // 한도를 넘겼거나 장애가 난 경우. 여기까지 채운 것은 남기고 멈춥니다.
                log.warn("일괄 번역을 {}번째에서 중단했습니다: {}", from, e.toString());
                skipped += slots.size() - from;
                break;
            }
            for (int i = 0; i < chunk.size(); i++) {
                var slot = chunk.get(i);
                var english = translated.get(i).trim();
                // 영어가 길어져 칸을 넘치면 잘라 넣지 않고 건너뜁니다.
                // 중간에서 끊긴 문장보다 한국어 원문을 보여주는 편이 낫습니다.
                if (english.isEmpty() || english.length() > slot.max()) { skipped++; continue; }
                slot.apply().accept(english);
                filled.merge(slot.group(), 1, Integer::sum);
            }
        }

        // 지도 도면의 영어 이미지는 번역이 아니라 따로 만든 파일이라 여기서 채우지 않습니다.
        long floorplansWithoutEnglish = floorplans.findAll().stream()
                .filter(f -> f.getImageUrlEn() == null).count();

        return Map.of("filled", filled, "filledTotal", filled.values().stream().mapToInt(Integer::intValue).sum(),
                "skipped", skipped, "floorplansWithoutEnglishImage", floorplansWithoutEnglish);
    }

    private void add(List<Slot> slots, String group, String korean, String english, int max, Consumer<String> apply) {
        if (english != null || korean == null || korean.isBlank()) return;
        // DeepL 한 건의 상한. 이보다 긴 원문은 번역해도 칸에 들어가지 않습니다.
        if (korean.length() > 1000) return;
        slots.add(new Slot(group, korean, max, apply));
    }
}
