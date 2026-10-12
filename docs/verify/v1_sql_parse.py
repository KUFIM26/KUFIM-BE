# -*- coding: utf-8 -*-
"""schema.sql / seed_dev.sql 을 MySQL 방언으로 파싱해 구문 오류를 잡는다."""
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
import os as _os
_HERE = _os.path.dirname(_os.path.abspath(__file__))
_REPO = _os.path.abspath(_os.path.join(_HERE, '..', '..'))
_DOCS = _os.path.join(_REPO, 'docs')
_TMP  = _os.path.join(_HERE, '.tmp')
_os.makedirs(_TMP, exist_ok=True)

import sqlglot
from sqlglot import exp

ROOT = _REPO
issues = []

for path in ['docs/sql/schema.sql', 'docs/sql/seed_dev.sql']:
    full = ROOT + '\\' + path.replace('/', '\\')
    src = open(full, encoding='utf-8').read()
    print('=' * 64)
    print(path)
    try:
        stmts = [s for s in sqlglot.parse(src, dialect='mysql') if s is not None]
    except Exception as e:
        print('  PARSE FAIL:', type(e).__name__, str(e)[:600])
        issues.append((path, 'parse', str(e)[:200]))
        continue
    kinds = {}
    for s in stmts:
        kinds[type(s).__name__] = kinds.get(type(s).__name__, 0) + 1
    print('  parsed statements: %d' % len(stmts))
    print('  kinds:', kinds)

# ---- 스키마 구조 추출 -------------------------------------------------
schema_src = open(_os.path.join(_DOCS,'sql','schema.sql'), encoding='utf-8').read()
stmts = [s for s in sqlglot.parse(schema_src, dialect='mysql') if s is not None]

tables = {}      # name -> {col: type}
pks = {}
uniques = {}     # name -> list[tuple(cols)]
fks = []         # (table, cols, ref_table, ref_cols)
indexes = {}

for s in stmts:
    if isinstance(s, exp.Create) and s.kind == 'TABLE':
        tname = s.this.this.name if hasattr(s.this, 'this') else None
        if tname is None:
            tname = s.find(exp.Table).name
        cols = {}
        for cd in s.find_all(exp.ColumnDef):
            cols[cd.name] = cd.args['kind'].sql(dialect='mysql') if cd.args.get('kind') else '?'
        tables[tname] = cols
        uniques[tname] = []
        indexes[tname] = []
        # 제약
        schema_expr = s.this
        for e in schema_expr.expressions:
            if isinstance(e, exp.PrimaryKey):
                pks[tname] = [c.name for c in e.expressions]
            elif isinstance(e, exp.UniqueColumnConstraint):
                pass
            elif isinstance(e, exp.Constraint) or isinstance(e, exp.ForeignKey):
                pass
        # FK / UNIQUE KEY / KEY 는 원문 정규식으로 보강 추출
    elif isinstance(s, exp.Alter):
        pass

print()
print('=' * 64)
print('테이블 %d개 추출' % len(tables))
for t, cols in tables.items():
    print('  %-24s cols=%d' % (t, len(cols)))

import json
out = _os.path.join(_TMP, 'schema_tables.json')
json.dump(tables, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('\n-> %s 저장' % out)
