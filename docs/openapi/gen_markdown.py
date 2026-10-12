# -*- coding: utf-8 -*-
"""
OpenAPI 명세에서 마크다운 상세 참조 문서를 생성한다.

    python docs/openapi/gen_markdown.py

목적
  Swagger UI 를 띄우지 않고도 엔드포인트별 요청·응답·에러를 읽을 수 있게 한다.
  손으로 쓰지 않고 생성하므로 OpenAPI 와 영원히 어긋나지 않는다.

출력
  docs/KUFIM_API_상세참조.md   (전량 자동 생성 — 직접 수정하지 말 것)
"""
import io
import os
import sys
import yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.abspath(os.path.join(HERE, '..'))
SPEC = os.path.join(HERE, 'kufim-openapi.yaml')
OUT = os.path.join(DOCS, 'KUFIM_API_상세참조.md')

spec = yaml.safe_load(open(SPEC, encoding='utf-8'))
SCHEMAS = spec['components']['schemas']
PARAMS = spec['components']['parameters']
RESPONSES = spec['components']['responses']

METHODS = ['get', 'post', 'put', 'patch', 'delete']

TAG_KO = {t['name']: t.get('description', t['name']) for t in spec.get('tags', [])}


# ------------------------------------------------------------------ 유틸
def deref(node):
    """$ref 를 한 단계 푼다."""
    if isinstance(node, dict) and '$ref' in node:
        ref = node['$ref']
        name = ref.split('/')[-1]
        if '/schemas/' in ref:
            return SCHEMAS.get(name, {})
        if '/parameters/' in ref:
            return PARAMS.get(name, {})
        if '/responses/' in ref:
            return RESPONSES.get(name, {})
    return node


def ref_name(node):
    if isinstance(node, dict) and '$ref' in node:
        return node['$ref'].split('/')[-1]
    return None


def type_str(sch):
    """스키마를 사람이 읽는 타입 문자열로."""
    if not isinstance(sch, dict):
        return '-'
    name = ref_name(sch)
    if name:
        return '[`%s`](#%s)' % (name, name.lower())
    if 'enum' in sch:
        return ' \\| '.join('`%s`' % v for v in sch['enum'])
    if 'const' in sch:
        return '`%s` (고정)' % sch['const']
    if 'oneOf' in sch:
        return ' 또는 '.join(type_str(x) for x in sch['oneOf'])
    if 'allOf' in sch:
        inner = [ref_name(x) for x in sch['allOf'] if ref_name(x)]
        return '[`%s`](#%s) 확장' % (inner[0], inner[0].lower()) if inner else 'object'

    t = sch.get('type', '-')
    if isinstance(t, list):
        base = [x for x in t if x != 'null']
        nullable = 'null' in t
        t = base[0] if base else 'null'
        suffix = ' (nullable)' if nullable else ''
    else:
        suffix = ''

    if t == 'array':
        return '`%s[]`%s' % (type_str(sch.get('items', {})).strip('`[]'), suffix)
    if t == 'string' and sch.get('format'):
        return '`string(%s)`%s' % (sch['format'], suffix)
    if t == 'integer' and sch.get('format'):
        return '`integer`%s' % suffix
    return '`%s`%s' % (t, suffix)


def constraints(sch):
    """제약 조건을 문자열로."""
    if not isinstance(sch, dict):
        return ''
    out = []
    if 'minimum' in sch or 'maximum' in sch:
        out.append('%s ~ %s' % (sch.get('minimum', ''), sch.get('maximum', '')))
    if 'minLength' in sch or 'maxLength' in sch:
        out.append('길이 %s~%s' % (sch.get('minLength', ''), sch.get('maxLength', '')))
    if 'minItems' in sch:
        out.append('최소 %d개' % sch['minItems'])
    if 'pattern' in sch:
        out.append('형식 `%s`' % sch['pattern'])
    if 'default' in sch:
        out.append('기본값 `%s`' % sch['default'])
    return ', '.join(out)


def clean(text):
    """설명 문자열을 표 한 칸에 넣을 수 있게 정리."""
    if not text:
        return ''
    return ' '.join(str(text).split()).replace('|', '\\|')


def props_table(schema, indent=''):
    """스키마의 속성을 표로."""
    sch = deref(schema)
    if not isinstance(sch, dict):
        return ''
    if 'allOf' in sch:
        merged = {'properties': {}, 'required': []}
        for part in sch['allOf']:
            pd = deref(part)
            merged['properties'].update(pd.get('properties', {}))
            merged['required'] += pd.get('required', [])
        sch = merged
    props = sch.get('properties')
    if not props:
        return ''
    req = set(sch.get('required', []))
    lines = ['| 필드 | 타입 | 필수 | 제약 | 설명 |', '|---|---|---|---|---|']
    for k, v in props.items():
        c = constraints(v)
        lines.append('| `%s` | %s | %s | %s | %s |'
                     % (k, type_str(v), '✅' if k in req else '—', c or '—',
                        clean(v.get('description', ''))))
    return '\n'.join(lines)


def body_schema(op):
    rb = op.get('requestBody')
    if not rb:
        return None, None
    content = rb.get('content', {})
    for ct in ('application/json', 'multipart/form-data'):
        if ct in content:
            return ct, content[ct].get('schema')
    return None, None


def data_schema(resp):
    """봉투 안의 data 스키마를 꺼낸다."""
    content = resp.get('content', {}).get('application/json', {})
    sch = content.get('schema')
    if not isinstance(sch, dict):
        return None
    if 'allOf' in sch:
        for part in sch['allOf']:
            if isinstance(part, dict) and 'properties' in part and 'data' in part['properties']:
                return part['properties']['data']
    return sch


# ------------------------------------------------------------------ 본문 생성
def render():
    L = []
    A = L.append

    A('# KUFIM API 상세 참조')
    A('')
    A('> **이 문서는 자동 생성됩니다. 직접 수정하지 마세요.**')
    A('> 원본은 [`openapi/kufim-openapi.yaml`](./openapi/kufim-openapi.yaml) 이며,')
    A('> `python docs/openapi/gen_markdown.py` 로 다시 만듭니다.')
    A('>')
    A('> 설계 배경·공통 규약·에러 코드 체계는 [API 명세서](./KUFIM_API명세서.md)를 보세요.')
    A('')

    ops = []
    for path, item in spec['paths'].items():
        common = item.get('parameters', [])
        for m in METHODS:
            if m in item:
                ops.append((path, m, item[m], common))

    A('| 항목 | 내용 |')
    A('|---|---|')
    A('| 생성 원본 | `%s` |' % os.path.basename(SPEC))
    A('| API 버전 | %s |' % spec['info']['version'])
    A('| 엔드포인트 | %d개 |' % len(ops))
    A('| 스키마 | %d개 |' % len(SCHEMAS))
    A('')
    A('---')
    A('')

    # 태그별 그룹
    by_tag = {}
    for path, m, op, common in ops:
        tag = (op.get('tags') or ['기타'])[0]
        by_tag.setdefault(tag, []).append((path, m, op, common))

    # 목차
    A('## 목차')
    A('')
    for t in spec.get('tags', []):
        name = t['name']
        if name not in by_tag:
            continue
        A('- **%s** — %s (%d개)' % (name, t.get('description', ''), len(by_tag[name])))
    A('')
    A('---')
    A('')

    for t in spec.get('tags', []):
        tag = t['name']
        if tag not in by_tag:
            continue
        A('## %s' % t.get('description', tag))
        A('')
        A('`%s` 그룹 · %d개 엔드포인트' % (tag, len(by_tag[tag])))
        A('')

        for path, m, op, common in by_tag[tag]:
            A('### `%s %s`' % (m.upper(), path))
            A('')
            summary = op.get('summary', '')
            if summary:
                A('**%s**' % summary)
                A('')
            desc = op.get('description', '')
            if desc:
                for line in desc.strip().split('\n'):
                    A(line.strip())
                A('')

            # 권한
            sec = op.get('security', spec.get('security', []))
            if any('sessionAuth' in x for x in sec):
                A('**인증** 관리자 세션 쿠키 필요')
            else:
                A('**인증** 불필요 (이용자 API)')
            A('')

            # 파라미터
            allp = list(common) + list(op.get('parameters', []))
            if allp:
                rows = []
                for pr in allp:
                    d = deref(pr)
                    rows.append('| `%s` | %s | %s | %s | %s |'
                                % (d.get('name', '?'), d.get('in', '?'),
                                   '✅' if d.get('required') else '—',
                                   type_str(d.get('schema', {})),
                                   clean(d.get('description', ''))))
                A('**파라미터**')
                A('')
                A('| 이름 | 위치 | 필수 | 타입 | 설명 |')
                A('|---|---|---|---|---|')
                L.extend(rows)
                A('')

            # 요청 본문
            ct, bs = body_schema(op)
            if bs is not None:
                A('**요청 본문** (`%s`)' % ct)
                A('')
                bsd = deref(bs)
                if bsd.get('description'):
                    A('> %s' % clean(bsd['description']))
                    A('')
                tbl = props_table(bs)
                if tbl:
                    A(tbl)
                    A('')

            # 응답
            A('**응답**')
            A('')
            A('| 코드 | 설명 | 본문 |')
            A('|---|---|---|')
            for code, resp in sorted(op.get('responses', {}).items()):
                r = deref(resp)
                ds = data_schema(r)
                shape = type_str(ds) if ds is not None else '—'
                if 'image/png' in r.get('content', {}):
                    shape = 'PNG 바이너리'
                elif 'application/pdf' in r.get('content', {}):
                    shape = 'PDF 바이너리'
                elif 'text/csv' in r.get('content', {}):
                    shape = 'CSV'
                A('| `%s` | %s | %s |' % (code, clean(r.get('description', '')), shape))
            A('')
            A('---')
            A('')

    # 스키마 사전
    A('## 스키마 사전')
    A('')
    A('응답·요청에 쓰이는 데이터 구조다.')
    A('')
    for name in sorted(SCHEMAS):
        sch = SCHEMAS[name]
        A('### %s' % name)
        A('')
        if sch.get('description'):
            for line in str(sch['description']).strip().split('\n'):
                A('> %s' % line.strip())
            A('')
        if 'enum' in sch:
            A('열거형: %s' % ' · '.join('`%s`' % v for v in sch['enum']))
            A('')
        tbl = props_table(sch)
        if tbl:
            A(tbl)
            A('')

    return '\n'.join(L) + '\n'


if __name__ == '__main__':
    out = render()
    open(OUT, 'w', encoding='utf-8').write(out)
    print('생성 완료: %s' % OUT)
    print('  %d자 / %d줄' % (len(out), out.count('\n')))
