# -*- coding: utf-8 -*-
"""문서 6종 + SQL 2종의 교차 정합성 검증."""
import sys, io, re, os, json
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
import os as _os
_HERE = _os.path.dirname(_os.path.abspath(__file__))
_REPO = _os.path.abspath(_os.path.join(_HERE, '..', '..'))
_DOCS = _os.path.join(_REPO, 'docs')
_TMP  = _os.path.join(_HERE, '.tmp')
_os.makedirs(_TMP, exist_ok=True)


ROOT = _DOCS
def read(n): return open(os.path.join(ROOT, n), encoding='utf-8').read()

SRS  = read('KUFIM_요구사항명세서.md')
FUNC = read('KUFIM_기능정의서.md')
API  = read('KUFIM_API명세서.md')
ERD  = read('KUFIM_ERD.md')
ARCH = read('KUFIM_시스템아키텍처.md')
TECH = read('KUFIM_기술스택정의서.md')
RDME = read('README.md')
SCHEMA = open(os.path.join(ROOT, 'sql', 'schema.sql'), encoding='utf-8').read()
SEED   = open(os.path.join(ROOT, 'sql', 'seed_dev.sql'), encoding='utf-8').read()

ALL = {'요구사항명세서': SRS, '기능정의서': FUNC, 'API명세서': API,
       'ERD': ERD, '시스템아키텍처': ARCH, '기술스택정의서': TECH, 'README': RDME}

ISSUES = []
def issue(sev, cat, msg):
    ISSUES.append((sev, cat, msg))
    print('  [%s] %s' % (sev, msg))
def ok(msg): print('  [ok]   %s' % msg)

# ================================================================= 1
print('=' * 68); print('1) 요구사항(FR) -> 기능(F-ID) 커버리지'); print('=' * 68)

FR_DOMAINS = ['COM','PRG','STG','WAI','MAP','ADM','I18N']
# 요구사항 표에서 정의된 FR ID (행 시작 | ID |)
fr_defined = set()
for d in FR_DOMAINS:
    for m in re.finditer(r'^\|\s*(%s-\d{3})\s*\|' % d, SRS, re.M):
        fr_defined.add(m.group(1))
nfr_defined = set(re.findall(r'^\|\s*(NFR-[A-Z]-\d{3})\s*\|', SRS, re.M))
print('  FR 정의: %d개, NFR 정의: %d개' % (len(fr_defined), len(nfr_defined)))

# 기능정의서가 참조하는 FR
fr_referenced = set()
for d in FR_DOMAINS:
    fr_referenced |= set(re.findall(r'\b(%s-\d{3})\b' % d, FUNC))
missing = sorted(fr_defined - fr_referenced)
if missing:
    issue('FAIL', 'coverage', '기능정의서가 참조하지 않는 FR: %s' % ', '.join(missing))
else:
    ok('모든 FR이 기능정의서에서 참조됨')

ghost = sorted(fr_referenced - fr_defined)
if ghost:
    issue('FAIL', 'coverage', '기능정의서가 참조하는 미정의 FR: %s' % ', '.join(ghost))
else:
    ok('미정의 FR 참조 없음')

# ================================================================= 2
print(); print('=' * 68); print('2) 기능(F-ID) 정의 및 참조'); print('=' * 68)
f_defined = set(re.findall(r'^\|\s*(F-[A-Z0-9]+-\d{2})\s*\|', FUNC, re.M))
print('  F-ID 정의: %d개' % len(f_defined))
by_dom = {}
for f in f_defined:
    d = f.split('-')[1]; by_dom[d] = by_dom.get(d, 0) + 1
print('  도메인별:', dict(sorted(by_dom.items())))

f_in_api = set(re.findall(r'\b(F-[A-Z0-9]+-\d{2})\b', API))
f_in_erd = set(re.findall(r'\b(F-[A-Z0-9]+-\d{2})\b', ERD))
f_in_arch = set(re.findall(r'\b(F-[A-Z0-9]+-\d{2})\b', ARCH))
for nm, s in [('API명세서', f_in_api), ('ERD', f_in_erd), ('시스템아키텍처', f_in_arch)]:
    bad = sorted(s - f_defined)
    if bad: issue('FAIL', 'fid', '%s가 참조하는 미정의 F-ID: %s' % (nm, ', '.join(bad)))
    else: ok('%s의 F-ID 참조 모두 유효 (%d개 참조)' % (nm, len(s)))

# ================================================================= 3
print(); print('=' * 68); print('3) API 엔드포인트 집계'); print('=' * 68)
# 코드펜스 안의 예시는 제외하기 위해 fence 제거
api_nofence = re.sub(r'```.*?```', '', API, flags=re.S)
METH = r'(GET|POST|PUT|PATCH|DELETE)'
eps = set()
# 헤더형: #### `GET /path`
for m in re.finditer(r'^####\s+`%s\s+([^`]+)`' % METH, api_nofence, re.M):
    eps.add((m.group(1), m.group(2).strip()))
# 표형: | GET | `/path` |
for m in re.finditer(r'^\|\s*%s\s*\|\s*`([^`]+)`' % METH, api_nofence, re.M):
    for meth in m.group(1).split('/'):
        eps.add((meth.strip(), m.group(2).strip()))
# GET/POST 병기형
for m in re.finditer(r'^\|\s*(GET/POST|PATCH/DELETE)\s*\|\s*`([^`]+)`', api_nofence, re.M):
    for meth in m.group(1).split('/'):
        eps.add((meth.strip(), m.group(2).strip()))

pub = sorted(e for e in eps if not e[1].startswith('/admin'))
adm = sorted(e for e in eps if e[1].startswith('/admin'))
print('  이용자 엔드포인트: %d' % len(pub))
print('  관리자 엔드포인트: %d' % len(adm))
print('  합계: %d' % len(eps))

# 문서가 주장하는 수치
claim_pub = re.search(r'\*\*이용자 API (\d+)개\*\*', API)
if claim_pub:
    c = int(claim_pub.group(1))
    if c != len(pub):
        issue('WARN', 'count', 'API명세서 주장 "이용자 API %d개" vs 실제 추출 %d개' % (c, len(pub)))
    else: ok('이용자 API 개수 일치 (%d)' % c)

# ================================================================= 4
print(); print('=' * 68); print('4) 에러 코드 정의 vs 사용'); print('=' * 68)
# §5 에러 코드 목록에서 정의된 코드
sec5 = API[API.find('## 5. 에러 코드 목록'):API.find('## 6. 엔드포인트 요약')]
defined_err = set(re.findall(r'^\|\s*`([A-Z]\d{3})`\s*\|', sec5, re.M))
print('  정의된 에러 코드: %d개 -> %s' % (len(defined_err), ', '.join(sorted(defined_err))))
used_err = set(re.findall(r'\b([CWBSMAI]\d{3})\b', API)) | set(re.findall(r'\b([CWBSMAI]\d{3})\b', ARCH))
used_err = {e for e in used_err if re.match(r'^[CWBSMAI]\d{3}$', e)}
undef = sorted(used_err - defined_err)
unused = sorted(defined_err - used_err)
if undef: issue('FAIL', 'error', '정의되지 않은 에러 코드 사용: %s' % ', '.join(undef))
else: ok('사용된 에러 코드 모두 정의됨')
if unused: issue('INFO', 'error', '정의만 되고 본문에서 미사용: %s' % ', '.join(unused))
else: ok('정의된 코드 모두 사용됨')

# ================================================================= 5
print(); print('=' * 68); print('5) 테이블명: ERD 문서 vs schema.sql'); print('=' * 68)
sql_tables = set(re.findall(r'CREATE TABLE (\w+)', SCHEMA))
erd_tables = set(re.findall(r'^### 3\.\d+ `(\w+)`', ERD, re.M))
print('  schema.sql: %d개' % len(sql_tables))
print('  ERD 문서 상세 절: %d개' % len(erd_tables))
d1 = sorted(sql_tables - erd_tables); d2 = sorted(erd_tables - sql_tables)
if d1: issue('FAIL', 'table', 'schema.sql 에만 있는 테이블: %s' % ', '.join(d1))
if d2: issue('FAIL', 'table', 'ERD 문서에만 있는 테이블: %s' % ', '.join(d2))
if not d1 and not d2: ok('테이블 집합 일치')

# ERD mermaid 블록의 엔티티도 대조
mer = re.search(r'```mermaid\s*\nerDiagram(.*?)```', ERD, re.S)
if mer:
    ents = set(x.lower() for x in re.findall(r'^\s{4}([A-Z_]+)\s*\{', mer.group(1), re.M))
    d3 = sorted(sql_tables - ents); d4 = sorted(ents - sql_tables)
    if d3: issue('FAIL', 'table', 'ERD 다이어그램에 누락된 테이블: %s' % ', '.join(d3))
    if d4: issue('FAIL', 'table', 'ERD 다이어그램에만 있는 엔티티: %s' % ', '.join(d4))
    if not d3 and not d4: ok('ERD 다이어그램 엔티티 %d개 일치' % len(ents))

# ================================================================= 6
print(); print('=' * 68); print('6) 컬럼명: ERD 문서 vs schema.sql'); print('=' * 68)
# schema 의 테이블별 컬럼
sql_cols = {}
for m in re.finditer(r'CREATE TABLE (\w+) \((.*?)\n\)\s*ENGINE', SCHEMA, re.S):
    t, body = m.group(1), m.group(2)
    cols = []
    for line in body.split('\n'):
        line = line.strip()
        mm = re.match(r'^(\w+)\s+(BIGINT|INT|VARCHAR|CHAR|TEXT|BOOLEAN|DATETIME|TINYINT|DECIMAL)', line, re.I)
        if mm: cols.append(mm.group(1))
    sql_cols[t] = cols
tot = sum(len(v) for v in sql_cols.values())
print('  schema.sql 총 컬럼 수: %d' % tot)

mismatch = 0
for m in re.finditer(r'^### 3\.\d+ `(\w+)`.*?\n(.*?)(?=^### |\Z)', ERD, re.S | re.M):
    t, body = m.group(1), m.group(2)
    if t not in sql_cols: continue
    # '인덱스' 표 구간은 컬럼 표가 아니므로 제외
    idxpos = body.find('**인덱스**')
    if idxpos >= 0:
        body = body[:idxpos]
    doc_cols = set()
    for mm in re.finditer(r'^\|\s*`([a-z_]+)`(?:\s*/\s*`([a-z_]+)`)?\s*\|', body, re.M):
        doc_cols.add(mm.group(1))
        if mm.group(2): doc_cols.add(mm.group(2))
    real = set(sql_cols[t])
    # created_at/updated_at 은 문서에서 생략 가능
    only_doc = sorted(doc_cols - real)
    only_sql = sorted(real - doc_cols - {'created_at', 'updated_at'})
    if only_doc:
        issue('FAIL', 'column', '%s: ERD 문서에만 있는 컬럼 %s' % (t, only_doc)); mismatch += 1
    if only_sql:
        issue('WARN', 'column', '%s: schema.sql 에만 있는 컬럼 %s' % (t, only_sql)); mismatch += 1
if mismatch == 0: ok('ERD 문서 컬럼과 schema.sql 컬럼 일치')

# ================================================================= 7
print(); print('=' * 68); print('7) enum 값: 기능정의서 vs schema.sql 주석 vs API명세서'); print('=' * 68)
ENUM_SETS = {
    'waiting.status': {'WAITING','CALLED','ENTERED','CANCELED','NO_SHOW'},
    'congestion.level': {'SMOOTH','NORMAL','CROWDED','VERY_CROWDED'},
    'booth.category': {'EXPERIENCE','FOOD','FOOD_TRUCK','PROMOTION','PERFORMANCE'},
    'facility.type': {'RESTROOM','CONTROL_ROOM','MEDICAL','SMOKING_AREA','TRASH','ENTRANCE'},
    'admin.role': {'SUPER_ADMIN','BOOTH_ADMIN'},
    'entry_status': {'OPEN','RESTRICTED'},
    'accepting_status': {'OPEN','CLOSED','DISABLED'},
}
for name, vals in ENUM_SETS.items():
    where = []
    for v in vals:
        docs_with = [d for d, txt in ALL.items() if re.search(r'\b%s\b' % v, txt)]
        if not docs_with:
            issue('FAIL', 'enum', '%s 값 %s 가 어떤 문서에도 없음' % (name, v))
        where.append((v, len(docs_with)))
    ok('%s: %s' % (name, ', '.join('%s(%d문서)' % w for w in where)))

# schema.sql 주석의 enum 과 대조
for col, expected in [('status', {'WAITING','CALLED','ENTERED','CANCELED','NO_SHOW'}),
                      ('level', {'SMOOTH','NORMAL','CROWDED','VERY_CROWDED'})]:
    for m in re.finditer(r'--\s*([A-Z_]+(?:\s*\|\s*[A-Z_]+)+)', SCHEMA):
        got = set(x.strip() for x in m.group(1).split('|'))
        if got == expected:
            ok('schema.sql 주석 enum 일치: %s' % col); break

# ================================================================= 8
print(); print('=' * 68); print('8) 내부 링크 검증'); print('=' * 68)
badlink = 0
for docname, txt in ALL.items():
    for m in re.finditer(r'\]\((\./[^)#]+)(#[^)]*)?\)', txt):
        rel = m.group(1)
        target = os.path.normpath(os.path.join(ROOT, rel))
        if not os.path.exists(target):
            issue('FAIL', 'link', '%s -> 존재하지 않는 링크 %s' % (docname, rel)); badlink += 1
if badlink == 0: ok('모든 상대 링크가 실제 파일을 가리킴')

# ================================================================= 9
print(); print('=' * 68); print('9) README 수치 주장 검증'); print('=' * 68)
screens = set(re.findall(r'^\|\s*(U-\d{2}|A-\d{2})\s*\|', FUNC, re.M))
actual = {'테이블': len(sql_tables), '화면': len(screens), '기능': len(f_defined),
          '에러 코드': len(defined_err), 'REST': len(eps)}
claims = {}
for label, unit in [('테이블','개'),('화면','개'),('기능','개'),('에러 코드','종'),('REST','개')]:
    for m in re.finditer(r'%s (\d+)%s' % (label, unit), RDME):
        claims['%s %s%s' % (label, m.group(1), unit)] = (int(m.group(1)) == actual[label])
print('  실제: 테이블 %d / 화면 %d / 기능 %d / 에러코드 %d / 엔드포인트 %d'
      % (len(sql_tables), len(screens), len(f_defined), len(defined_err), len(eps)))
for k, v in claims.items():
    if v: ok('README 주장 "%s" 일치' % k)
    else: issue('FAIL', 'count', 'README 주장 "%s" 불일치' % k)

m = re.search(r'REST (\d+)개, WebSocket 토픽 (\d+)개, 에러 코드 (\d+)종', RDME)
if m:
    if int(m.group(1)) != len(eps):
        issue('FAIL', 'count', 'README "REST %s개" vs 실제 %d개' % (m.group(1), len(eps)))
    else: ok('README REST 엔드포인트 수 일치')
    if int(m.group(3)) != len(defined_err):
        issue('FAIL', 'count', 'README "에러 코드 %s종" vs 실제 %d종' % (m.group(3), len(defined_err)))

topics = set(re.findall(r'`(/topic/[^`]+)`', API))
print('  WebSocket 토픽: %d개 -> %s' % (len(topics), sorted(topics)))

# ================================================================= 10
print(); print('=' * 68); print('10) TBD / 결정 항목 일관성'); print('=' * 68)
tbd_srs = set(re.findall(r'\b(TBD-\d{2})\b', SRS))
tbd_all = set()
for t in ALL.values(): tbd_all |= set(re.findall(r'\b(TBD-\d{2})\b', t))
ts_all = set()
for t in ALL.values(): ts_all |= set(re.findall(r'\b(TS-\d{2})\b', t))
print('  TBD: %s' % ', '.join(sorted(tbd_all)))
print('  TS : %s' % ', '.join(sorted(ts_all)))
undef_tbd = sorted(tbd_all - tbd_srs)
if undef_tbd: issue('FAIL', 'tbd', '요구사항명세서 §9에 정의되지 않은 TBD: %s' % ', '.join(undef_tbd))
else: ok('모든 TBD가 요구사항명세서에 정의됨')
tbd_readme = set(re.findall(r'\b(TBD-\d{2})\b', RDME))
if tbd_all - tbd_readme:
    issue('WARN', 'tbd', 'README 결정대기 표에 누락된 TBD: %s' % ', '.join(sorted(tbd_all - tbd_readme)))
else: ok('README가 모든 TBD를 수록')

print(); print('=' * 68)
f = len([i for i in ISSUES if i[0] == 'FAIL'])
w = len([i for i in ISSUES if i[0] == 'WARN'])
n = len([i for i in ISSUES if i[0] == 'INFO'])
print('결과: FAIL %d / WARN %d / INFO %d' % (f, w, n))
json.dump([{'sev': a, 'cat': b, 'msg': c} for a, b, c in ISSUES],
          open(_os.path.join(_TMP, 'doc_issues.json'), 'w', encoding='utf-8'),
          ensure_ascii=False, indent=1)
