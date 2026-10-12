# -*- coding: utf-8 -*-
"""
design/tokens.css 를 직접 읽어 WCAG 명도 대비를 검증한다.
토큰 값을 하드코딩하지 않으므로 CSS 를 고치면 이 검사도 따라간다.

검증 항목
  COM-004    본문 명도 대비 4.5:1 이상
  NFR-U-002  야간 옥외 시인성
  NFR-U-003  색상 단독 정보 전달 금지 (색각 시뮬레이션으로 근거 확보)
  WCAG 1.4.11 비텍스트 대비 3:1 (상호작용 요소 경계)
"""
import sys, io, os, re, itertools
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

CSS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'design', 'tokens.css')

# ------------------------------------------------------------------ 색 계산
def hex2rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

def luminance(h):
    r, g, b = hex2rgb(h)
    return 0.2126*lin(r) + 0.7152*lin(g) + 0.0722*lin(b)

def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

def simulate(hx, kind):
    """Vienot/Brettel 근사 색각 이상 시뮬레이션."""
    r, g, b = [lin(v) for v in hex2rgb(hx)]
    L = 17.8824*r + 43.5161*g + 4.11935*b
    M = 3.45565*r + 27.1554*g + 3.86714*b
    S = 0.0299566*r + 0.184309*g + 1.46709*b
    if kind == 'protanopia':
        L, M, S = 2.02344*M - 2.52581*S, M, S
    elif kind == 'deuteranopia':
        L, M, S = L, 0.494207*L + 1.24827*S, S
    else:  # tritanopia
        L, M, S = L, M, -0.395913*L + 0.801109*M
    r2 = 0.0809444479*L - 0.130504409*M + 0.116721066*S
    g2 = -0.0102485335*L + 0.0540193266*M - 0.113614708*S
    b2 = -0.000365296938*L - 0.00412161469*M + 0.693511405*S
    def enc(c):
        c = max(0.0, min(1.0, c))
        c = 12.92*c if c <= 0.0031308 else 1.055*(c**(1/2.4)) - 0.055
        return int(round(max(0.0, min(1.0, c))*255))
    return '#%02X%02X%02X' % (enc(r2), enc(g2), enc(b2))

def dist(a, b):
    x, y = hex2rgb(a), hex2rgb(b)
    return sum((p-q)**2 for p, q in zip(x, y)) ** 0.5

# ------------------------------------------------------------------ 토큰 로드
if not os.path.exists(CSS):
    print('tokens.css 를 찾을 수 없음: %s' % CSS); sys.exit(1)
src = open(CSS, encoding='utf-8').read()
TOK = {m.group(1): m.group(2).upper()
       for m in re.finditer(r'(--[a-z0-9-]+)\s*:\s*(#[0-9A-Fa-f]{6})\s*;', src)}
print('tokens.css 에서 색상 토큰 %d개 로드' % len(TOK))

FAILS, WARNS = [], []
def t(name):
    if name not in TOK:
        FAILS.append('토큰 없음: %s' % name)
        return None
    return TOK[name]

def check(label, fg_tok, bg_tok, need):
    fg, bg = t(fg_tok), t(bg_tok)
    if fg is None or bg is None:
        print('  [FAIL] %-34s 토큰 누락' % label); return
    r = contrast(fg, bg)
    st = 'ok' if r >= need else 'FAIL'
    if r < need: FAILS.append('%s (%.2f < %.1f)' % (label, r, need))
    print('  [%-4s] %-34s %s on %s = %5.2f:1  (기준 %.1f)' % (st, label, fg, bg, r, need))

# ------------------------------------------------------------------ A) UI
print()
print('=' * 76)
print('A) UI 토큰 명도 대비')
print('=' * 76)
check('본문 / 배경',            '--color-text',      '--color-bg',      4.5)
check('본문 / 서피스',           '--color-text',      '--color-surface', 4.5)
check('보조 텍스트 / 배경',       '--color-text-sub',  '--color-bg',      4.5)
check('보조 텍스트 / 서피스',      '--color-text-sub',  '--color-surface', 4.5)
check('브랜드 버튼 텍스트',        '--color-brand-on',  '--color-brand',   4.5)
check('브랜드 텍스트 / 배경',      '--color-brand',     '--color-bg',      4.5)
check('링크 / 배경',             '--color-link',      '--color-bg',      4.5)
check('위험 / 배경',             '--color-danger',    '--color-bg',      4.5)
check('상호작용 테두리 / 배경',     '--color-border',    '--color-bg',      3.0)
check('포커스 링 / 배경',         '--color-focus',     '--color-bg',      3.0)

# ------------------------------------------------------------------ B) 혼잡도
LEVELS = ['smooth', 'normal', 'crowded', 'very-crowded']
print()
print('=' * 76)
print('B) 혼잡도 뱃지')
print('=' * 76)
for lv in LEVELS:
    check('혼잡도 %s 텍스트' % lv, '--congestion-%s-fg' % lv, '--congestion-%s-bg' % lv, 4.5)
print()
for lv in LEVELS:
    check('혼잡도 %s 테두리/페이지' % lv, '--congestion-%s-border' % lv, '--color-bg', 3.0)

# ------------------------------------------------------------------ C) 색각
print()
print('=' * 76)
print('C) 색각 이상 시뮬레이션')
print('=' * 76)
bgs = {lv: t('--congestion-%s-bg' % lv) for lv in LEVELS}
if all(bgs.values()):
    for kind in ('protanopia', 'deuteranopia', 'tritanopia'):
        sim = {lv: simulate(bgs[lv], kind) for lv in LEVELS}
        pair = min(itertools.combinations(LEVELS, 2),
                   key=lambda p: dist(sim[p[0]], sim[p[1]]))
        mn = dist(sim[pair[0]], sim[pair[1]])
        print('  %-14s 최소 색거리 %5.1f  (%s <-> %s)' % (kind, mn, pair[0], pair[1]))
    print()
    print('  참고: 혼잡도는 색 단독으로 정보를 전달하지 않는다 (NFR-U-003).')
    print('        칸 게이지와 한글 텍스트가 1차 구분 채널이므로 위 수치는 보조 지표다.')
    print('        구현 시 텍스트나 게이지를 생략한 축약형을 만들지 말 것.')

# ------------------------------------------------------------------ D) 규칙
print()
print('=' * 76)
print('D) 토큰 사용 규칙')
print('=' * 76)
base = re.search(r'--text-base:\s*(\d+)px', src)
if base and int(base.group(1)) >= 16:
    print('  [ok  ] --text-base %spx (COM-004 최소 16px)' % base.group(1))
else:
    FAILS.append('--text-base 가 16px 미만')
    print('  [FAIL] --text-base 가 16px 미만이거나 정의되지 않음')

touch = re.search(r'--touch-min:\s*(\d+)px', src)
if touch and int(touch.group(1)) >= 44:
    print('  [ok  ] --touch-min %spx (COM-004 최소 44px)' % touch.group(1))
else:
    FAILS.append('--touch-min 이 44px 미만')
    print('  [FAIL] --touch-min 이 44px 미만이거나 정의되지 않음')

if 'prefers-reduced-motion' in src:
    print('  [ok  ] prefers-reduced-motion 대응 있음')
else:
    WARNS.append('prefers-reduced-motion 미대응')
    print('  [WARN] prefers-reduced-motion 대응 없음')

if ':focus-visible' in src:
    print('  [ok  ] 포커스 표시 정의 있음')
else:
    FAILS.append('focus-visible 미정의')
    print('  [FAIL] 포커스 표시가 정의되지 않음')

# ------------------------------------------------------------------ 요약
print()
print('=' * 76)
print('결과: FAIL %d / WARN %d' % (len(FAILS), len(WARNS)))
for f in FAILS: print('  [FAIL] %s' % f)
for w in WARNS: print('  [WARN] %s' % w)
sys.exit(1 if FAILS else 0)
