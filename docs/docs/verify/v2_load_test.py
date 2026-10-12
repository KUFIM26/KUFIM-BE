# -*- coding: utf-8 -*-
"""
schema.sql + seed_dev.sql 을 SQLite 로 기계 번역해 실제 적재한다.
목적: FK 위반 / UNIQUE 위반 / CHECK 위반 / 참조 무결성을 실제로 검출.
MySQL 고유 구문은 번역하고, 번역 불가한 것(순환 FK ALTER)은 별도 수동 검증.
"""
import sys, io, re, sqlite3, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
import os as _os
_HERE = _os.path.dirname(_os.path.abspath(__file__))
_REPO = _os.path.abspath(_os.path.join(_HERE, '..', '..'))
_DOCS = _os.path.join(_REPO, 'docs')
_TMP  = _os.path.join(_HERE, '.tmp')
_os.makedirs(_TMP, exist_ok=True)


ROOT = _REPO
schema = open(_os.path.join(_DOCS,'sql','schema.sql'), encoding='utf-8').read()
seed = open(_os.path.join(_DOCS,'sql','seed_dev.sql'), encoding='utf-8').read()

FINDINGS = []
def note(sev, where, msg):
    FINDINGS.append((sev, where, msg))
    print('[%s] %-22s %s' % (sev, where, msg))

# ---------------------------------------------------------------- 번역
def strip_comments(s):
    return re.sub(r'--[^\n]*', '', s)

def translate(sql):
    out = sql
    out = re.sub(r'^\s*SET\s+[^;]+;', '', out, flags=re.M | re.I)
    # 테이블 옵션 제거
    out = re.sub(r'\)\s*ENGINE\s*=\s*InnoDB[^;]*;', ');', out, flags=re.I)
    # 자동증가 PK
    out = re.sub(r'BIGINT\s+NOT\s+NULL\s+AUTO_INCREMENT', 'INTEGER', out, flags=re.I)
    out = re.sub(r'\bAUTO_INCREMENT\b', '', out, flags=re.I)
    # 타입
    out = re.sub(r'\bDATETIME\(3\)', 'TEXT', out, flags=re.I)
    out = re.sub(r'\bTINYINT\b', 'INTEGER', out, flags=re.I)
    out = re.sub(r'\bBIGINT\b', 'INTEGER', out, flags=re.I)
    # 기본값 / ON UPDATE
    out = re.sub(r'\s+ON\s+UPDATE\s+CURRENT_TIMESTAMP\(3\)', '', out, flags=re.I)
    out = re.sub(r'CURRENT_TIMESTAMP\(3\)', 'CURRENT_TIMESTAMP', out, flags=re.I)
    # UNIQUE KEY name (...) -> CONSTRAINT name UNIQUE (...)
    out = re.sub(r'\bUNIQUE\s+KEY\s+(\w+)\s*\(', r'CONSTRAINT \1 UNIQUE (', out, flags=re.I)
    return out

def extract_plain_keys(sql):
    """KEY idx_x (cols) 를 뽑아 CREATE INDEX 로 분리하고 원문에서 제거."""
    idxs = []
    tables = re.findall(r'CREATE TABLE (\w+) \((.*?)\n\)\s*;', sql, flags=re.S)
    for tname, body in tables:
        for m in re.finditer(r'^\s*KEY\s+(\w+)\s*\(([^)]*)\)\s*,?\s*$', body, flags=re.M | re.I):
            idxs.append((m.group(1), tname, m.group(2).strip()))
    out = re.sub(r'^\s*KEY\s+\w+\s*\([^)]*\)\s*,?\s*$\n?', '', sql, flags=re.M | re.I)
    return out, idxs

s = strip_comments(schema)
s = translate(s)
s, indexes = extract_plain_keys(s)

# 순환 FK ALTER 는 SQLite 미지원 -> 분리 후 수동 검증
alter_stmts = re.findall(r'ALTER TABLE[^;]+;', s, flags=re.I | re.S)
s = re.sub(r'ALTER TABLE[^;]+;', '', s, flags=re.I | re.S)

# 마지막 컬럼 뒤 잔여 콤마 정리
s = re.sub(r',(\s*\))', r'\1', s)

con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys = ON')

print('=' * 66)
print('1) 스키마 적재')
print('=' * 66)
created = 0
for stmt in [x.strip() for x in s.split(';') if x.strip()]:
    try:
        con.execute(stmt)
        created += 1
    except Exception as e:
        note('FAIL', 'schema', '%s :: %s' % (str(e)[:120], stmt[:90].replace('\n', ' ')))
print('  CREATE TABLE 성공: %d' % created)

for name, tbl, cols in indexes:
    try:
        con.execute('CREATE INDEX %s ON %s (%s)' % (name, tbl, cols))
    except Exception as e:
        note('FAIL', 'index', '%s on %s(%s) :: %s' % (name, tbl, cols, str(e)[:100]))
print('  CREATE INDEX 성공: %d' % len(indexes))
print('  (SQLite 미지원으로 분리한 ALTER: %d개 -> 수동 검증)' % len(alter_stmts))

print()
print('=' * 66)
print('2) 시드 데이터 적재')
print('=' * 66)
sd = strip_comments(seed)
sd = re.sub(r'^\s*SET\s+[^;]+;', '', sd, flags=re.M | re.I)
ok = 0
for stmt in [x.strip() for x in sd.split(';') if x.strip()]:
    try:
        con.execute(stmt)
        ok += 1
    except Exception as e:
        note('FAIL', 'seed', '%s :: %s' % (str(e)[:150], stmt[:110].replace('\n', ' ')))
con.commit()
print('  INSERT/UPDATE 성공: %d' % ok)

print()
print('=' * 66)
print('3) 참조 무결성 검사 (PRAGMA foreign_key_check)')
print('=' * 66)
bad = con.execute('PRAGMA foreign_key_check').fetchall()
if bad:
    for b in bad:
        note('FAIL', 'fk_check', str(b))
else:
    print('  위반 없음')

print()
print('=' * 66)
print('4) 행 수')
print('=' * 66)
for (t,) in con.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"):
    n = con.execute('SELECT COUNT(*) FROM %s' % t).fetchone()[0]
    print('  %-24s %d' % (t, n))

con.commit()
import pickle
pickle.dump(None, open(os.devnull, 'wb'))
# 후속 검사에서 재사용하도록 파일로 덤프
dbfile = _os.path.join(_TMP, 'kufim_check.db')
if os.path.exists(dbfile):
    os.remove(dbfile)
disk = sqlite3.connect(dbfile)
con.backup(disk)
disk.close()
print('\n-> %s 저장' % dbfile)

print()
print('=' * 66)
print('요약: FAIL %d건' % len([f for f in FINDINGS if f[0] == 'FAIL']))
