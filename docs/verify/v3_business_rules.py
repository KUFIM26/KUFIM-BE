# -*- coding: utf-8 -*-
"""DB 제약으로 표현할 수 없는 업무 규칙을 시드 데이터에 대해 검증한다."""
import sys, io, sqlite3
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
import os as _os
_HERE = _os.path.dirname(_os.path.abspath(__file__))
_REPO = _os.path.abspath(_os.path.join(_HERE, '..', '..'))
_DOCS = _os.path.join(_REPO, 'docs')
_TMP  = _os.path.join(_HERE, '.tmp')
_os.makedirs(_TMP, exist_ok=True)


DB = _os.path.join(_TMP, 'kufim_check.db')
con = sqlite3.connect(DB)
con.row_factory = sqlite3.Row

FAILS = []
def check(name, rows, fmt=None):
    if rows:
        print('  [FAIL] %s -> %d건' % (name, len(rows)))
        for r in rows[:5]:
            print('         %s' % (fmt(r) if fmt else tuple(r)))
        FAILS.append(name)
    else:
        print('  [ok]   %s' % name)

# 문서에 정의된 enum (기능정의서 §4)
ENUMS = {
    ('festival', 'status'):        {'BEFORE', 'ONGOING', 'ENDED'},
    ('admin_account', 'role'):     {'SUPER_ADMIN', 'BOOTH_ADMIN'},
    ('stage', 'entry_status'):     {'OPEN', 'RESTRICTED'},
    ('congestion_log', 'level'):   {'SMOOTH', 'NORMAL', 'CROWDED', 'VERY_CROWDED'},
    ('performance', 'type'):       {'GUEST', 'STUDENT', 'EVENT'},
    ('booth', 'category'):         {'EXPERIENCE', 'FOOD', 'FOOD_TRUCK', 'PROMOTION', 'PERFORMANCE'},
    ('booth', 'accepting_status'): {'OPEN', 'CLOSED'},
    ('facility', 'type'):          {'RESTROOM', 'CONTROL_ROOM', 'MEDICAL', 'SMOKING_AREA', 'TRASH', 'ENTRANCE'},
    ('waiting', 'status'):         {'WAITING', 'CALLED', 'ENTERED', 'CANCELED', 'NO_SHOW'},
    ('waiting_event', 'to_status'):{'WAITING', 'CALLED', 'ENTERED', 'CANCELED', 'NO_SHOW'},
    ('waiting_event', 'actor_type'):{'USER', 'ADMIN', 'SYSTEM'},
    ('map_pin', 'target_type'):    {'BOOTH', 'FACILITY'},
    ('translation_cache', 'status'):{'AUTO', 'MANUAL', 'FAILED'},
}

print('=' * 66)
print('A) enum 값 검증')
print('=' * 66)
for (tbl, col), allowed in ENUMS.items():
    q = "SELECT DISTINCT %s AS v FROM %s WHERE %s IS NOT NULL" % (col, tbl, col)
    vals = [r['v'] for r in con.execute(q)]
    bad = [v for v in vals if v not in allowed]
    check('%s.%s' % (tbl, col), bad, fmt=lambda v: '허용되지 않는 값: %r' % v)

print()
print('=' * 66)
print('B) 다형 참조 무결성 (map_pin — DB FK 불가)')
print('=' * 66)
check('map_pin -> booth 존재',
      con.execute("""SELECT p.id, p.target_id FROM map_pin p
                     WHERE p.target_type='BOOTH'
                       AND NOT EXISTS (SELECT 1 FROM booth b WHERE b.id=p.target_id)""").fetchall())
check('map_pin -> facility 존재',
      con.execute("""SELECT p.id, p.target_id FROM map_pin p
                     WHERE p.target_type='FACILITY'
                       AND NOT EXISTS (SELECT 1 FROM facility f WHERE f.id=p.target_id)""").fetchall())
check('부스 중 핀 누락',
      con.execute("""SELECT b.id, b.name FROM booth b WHERE b.deleted=0
                     AND NOT EXISTS (SELECT 1 FROM map_pin p WHERE p.target_type='BOOTH' AND p.target_id=b.id)""").fetchall())
check('편의시설 중 핀 누락',
      con.execute("""SELECT f.id, f.name FROM facility f WHERE f.deleted=0
                     AND NOT EXISTS (SELECT 1 FROM map_pin p WHERE p.target_type='FACILITY' AND p.target_id=f.id)""").fetchall())

print()
print('=' * 66)
print('C) 웨이팅 상태 정합성 (기능정의서 §4.1 상태 머신)')
print('=' * 66)
check('CALLED/ENTERED/NO_SHOW 인데 called_at 없음',
      con.execute("""SELECT public_id, status FROM waiting
                     WHERE status IN ('CALLED','ENTERED','NO_SHOW') AND called_at IS NULL""").fetchall())
check('WAITING 인데 called_at 있음',
      con.execute("SELECT public_id FROM waiting WHERE status='WAITING' AND called_at IS NOT NULL").fetchall())
check('종료 상태인데 closed_at 없음',
      con.execute("""SELECT public_id, status FROM waiting
                     WHERE status IN ('ENTERED','CANCELED','NO_SHOW') AND closed_at IS NULL""").fetchall())
check('활성 상태인데 closed_at 있음',
      con.execute("""SELECT public_id, status FROM waiting
                     WHERE status IN ('WAITING','CALLED') AND closed_at IS NOT NULL""").fetchall())
check('시각 역전 (registered > called)',
      con.execute("SELECT public_id FROM waiting WHERE called_at IS NOT NULL AND called_at < registered_at").fetchall())
check('시각 역전 (called > closed)',
      con.execute("SELECT public_id FROM waiting WHERE closed_at IS NOT NULL AND called_at IS NOT NULL AND closed_at < called_at").fetchall())
check('ENTERED/NO_SHOW 인데 called_by 없음',
      con.execute("SELECT public_id, status FROM waiting WHERE status IN ('ENTERED','NO_SHOW') AND called_by IS NULL").fetchall())
check('중복 활성 대기 (동일 booth+client_token)',
      con.execute("""SELECT booth_id, client_token, COUNT(*) c FROM waiting
                     WHERE status IN ('WAITING','CALLED')
                     GROUP BY booth_id, client_token HAVING c > 1""").fetchall())
check('party_size 범위 이탈',
      con.execute("SELECT public_id, party_size FROM waiting WHERE party_size < 1 OR party_size > 10").fetchall())
check('waiting_seq < MAX(waiting_number)',
      con.execute("""SELECT b.id, b.waiting_seq, MAX(w.waiting_number) mx FROM booth b
                     JOIN waiting w ON w.booth_id=b.id GROUP BY b.id
                     HAVING b.waiting_seq < mx""").fetchall())
check('waiting_enabled=0 인데 웨이팅 존재',
      con.execute("""SELECT DISTINCT b.id, b.name FROM booth b JOIN waiting w ON w.booth_id=b.id
                     WHERE b.waiting_enabled=0""").fetchall())
check('booth_code 없는데 waiting_enabled=1',
      con.execute("SELECT id, name FROM booth WHERE waiting_enabled=1 AND booth_code IS NULL").fetchall())

print()
print('=' * 66)
print('D) waiting_event 이력 정합성')
print('=' * 66)
check('waiting 마다 최초 이벤트(to_status=WAITING) 누락',
      con.execute("""SELECT w.public_id FROM waiting w
                     WHERE NOT EXISTS (SELECT 1 FROM waiting_event e
                                       WHERE e.waiting_id=w.id AND e.from_status IS NULL AND e.to_status='WAITING')""").fetchall())
check('이벤트 최종 상태 != waiting.status',
      con.execute("""SELECT w.public_id, w.status, (
                        SELECT e.to_status FROM waiting_event e WHERE e.waiting_id=w.id
                        ORDER BY e.occurred_at DESC, e.id DESC LIMIT 1) last_st
                     FROM waiting w
                     WHERE w.status <> (SELECT e.to_status FROM waiting_event e WHERE e.waiting_id=w.id
                                        ORDER BY e.occurred_at DESC, e.id DESC LIMIT 1)""").fetchall())
# 상태 전이 허용 표
ALLOWED = {(None,'WAITING'),('WAITING','CALLED'),('WAITING','CANCELED'),
           ('CALLED','ENTERED'),('CALLED','NO_SHOW')}
rows = con.execute("SELECT waiting_id, from_status, to_status FROM waiting_event").fetchall()
badtrans = [r for r in rows if (r['from_status'], r['to_status']) not in ALLOWED]
check('허용되지 않은 상태 전이', badtrans, fmt=lambda r: '%s -> %s (waiting_id=%s)' % (r['from_status'], r['to_status'], r['waiting_id']))
check('ADMIN 이벤트인데 actor_id 없음',
      con.execute("SELECT waiting_id, to_status FROM waiting_event WHERE actor_type='ADMIN' AND actor_id IS NULL").fetchall())

print()
print('=' * 66)
print('E) 혼잡도 / 무대')
print('=' * 66)
check('latest_congestion_log_id 미설정 구역',
      con.execute("SELECT id, name FROM stage_zone WHERE latest_congestion_log_id IS NULL").fetchall())
check('latest_congestion_log_id 가 타 구역 로그를 가리킴',
      con.execute("""SELECT z.id, z.name FROM stage_zone z JOIN congestion_log c
                     ON c.id = z.latest_congestion_log_id WHERE c.stage_zone_id <> z.id""").fetchall())
check('latest 가 실제 최신이 아님',
      con.execute("""SELECT z.id, z.name FROM stage_zone z JOIN congestion_log c ON c.id=z.latest_congestion_log_id
                     WHERE EXISTS (SELECT 1 FROM congestion_log c2 WHERE c2.stage_zone_id=z.id
                                   AND c2.recorded_at > c.recorded_at)""").fetchall())
check('estimated_people 음수',
      con.execute("SELECT id FROM congestion_log WHERE estimated_people < 0").fetchall())

print()
print('=' * 66)
print('F) 공연 시간')
print('=' * 66)
check('end_at <= start_at',
      con.execute("SELECT id, title FROM performance WHERE end_at <= start_at").fetchall())
check('축제 기간을 벗어난 공연',
      con.execute("""SELECT p.id, p.title FROM performance p JOIN festival f ON f.id=p.festival_id
                     WHERE p.start_at < f.start_at OR p.end_at > f.end_at""").fetchall())
ov = con.execute("""SELECT a.id ia, b.id ib, a.title ta, b.title tb, a.stage_id FROM performance a
                    JOIN performance b ON a.stage_id=b.stage_id AND a.id < b.id
                    WHERE a.stage_id IS NOT NULL
                      AND a.start_at < b.end_at AND b.start_at < a.end_at""").fetchall()
check('동일 무대 공연 시간 겹침', ov, fmt=lambda r: '%s <-> %s (stage %s)' % (r['ta'], r['tb'], r['stage_id']))

print()
print('=' * 66)
print('G) 지도 좌표 보정 (MAP-006 / M001·M002)')
print('=' * 66)
pts = con.execute("SELECT lat, lng, x, y FROM map_calibration_point ORDER BY display_order").fetchall()
print('  기준점 수: %d' % len(pts))
if len(pts) < 3:
    print('  [FAIL] 기준점 3점 미만 -> M001'); FAILS.append('calibration count')
else:
    (a, b, c) = pts[0], pts[1], pts[2]
    # 위경도 3점의 외적 (일직선 판정)
    cross_geo = (b['lng']-a['lng'])*(c['lat']-a['lat']) - (b['lat']-a['lat'])*(c['lng']-a['lng'])
    cross_img = (b['x']-a['x'])*(c['y']-a['y']) - (b['y']-a['y'])*(c['x']-a['x'])
    print('  실좌표 외적 = %.9f' % cross_geo)
    print('  도면좌표 외적 = %.1f' % cross_img)
    if abs(cross_geo) < 1e-9:
        print('  [FAIL] 실좌표 기준점이 일직선 -> M002'); FAILS.append('collinear geo')
    else:
        print('  [ok]   실좌표 3점 비일직선')
    if abs(cross_img) < 1e-6:
        print('  [FAIL] 도면좌표 기준점이 일직선 -> M002'); FAILS.append('collinear img')
    else:
        print('  [ok]   도면좌표 3점 비일직선')

fp = con.execute("SELECT width, height FROM map_floorplan").fetchone()
check('핀 좌표가 도면 범위를 벗어남',
      con.execute("SELECT id, x, y FROM map_pin WHERE x < 0 OR y < 0 OR x > ? OR y > ?",
                  (fp['width'], fp['height'])).fetchall())
check('기준점 좌표가 도면 범위를 벗어남',
      con.execute("SELECT id, x, y FROM map_calibration_point WHERE x < 0 OR y < 0 OR x > ? OR y > ?",
                  (fp['width'], fp['height'])).fetchall())

print()
print('=' * 66)
print('H) 권한 / 공지')
print('=' * 66)
check('BOOTH_ADMIN 인데 담당 부스 없음',
      con.execute("""SELECT a.id, a.login_id FROM admin_account a
                     WHERE a.role='BOOTH_ADMIN' AND a.active=1
                       AND NOT EXISTS (SELECT 1 FROM admin_booth ab WHERE ab.admin_account_id=a.id)""").fetchall())
check('SUPER_ADMIN 인데 부스 매핑 보유(불필요)',
      con.execute("""SELECT a.id FROM admin_account a JOIN admin_booth ab ON ab.admin_account_id=a.id
                     WHERE a.role='SUPER_ADMIN'""").fetchall())
check('called_by 가 해당 부스 담당이 아님',
      con.execute("""SELECT w.public_id, w.called_by, w.booth_id FROM waiting w
                     WHERE w.called_by IS NOT NULL
                       AND NOT EXISTS (SELECT 1 FROM admin_booth ab
                                       WHERE ab.admin_account_id=w.called_by AND ab.booth_id=w.booth_id)
                       AND NOT EXISTS (SELECT 1 FROM admin_account aa
                                       WHERE aa.id=w.called_by AND aa.role='SUPER_ADMIN')""").fetchall())
check('published=1 인데 published_at 없음',
      con.execute("SELECT id, title FROM notice WHERE published=1 AND published_at IS NULL").fetchall())

print()
print('=' * 66)
print('결과: FAIL %d건' % len(FAILS))
if FAILS:
    for f in FAILS:
        print('  - %s' % f)
