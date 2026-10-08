# 보류 테이블

이전 ERD에서 보라색으로 표시한 항목입니다. 현재 JPA 엔터티와 Flyway 마이그레이션에는 넣지 않았습니다. 실제 도입 시 새 마이그레이션과 해당 기능 패키지를 추가합니다.

| 테이블 | 보류 이유 | 예정 관계 |
|---|---|---|
| stage_zone | 무대 구역별 UI 없음 | stage → stage_zone |
| congestion_log | 혼잡도 입력·이력 UI 없음 | stage_zone → congestion_log |
| booth_image | 부스 갤러리 UI 없음 | booth → booth_image |
| translation_cache | 다국어·번역 관리 없음 | 독립 캐시 |
| map_calibration_point | GPS와 도면 좌표 변환 없음 | map_floorplan → map_calibration_point |

도면 버전·업로드 이력, 공연 일정 변경 배지, 부스 별도 공지, 호출 간격 이동 평균도 보류 상태입니다. 현재 홈의 ‘보통’과 `stage.entry_status`의 OPEN/RESTRICTED는 아직 동일한 의미로 매핑하지 않습니다.
