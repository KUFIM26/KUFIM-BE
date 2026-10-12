# -*- coding: utf-8 -*-
"""
KUFIM 산출물 검증 일괄 실행.

    python docs/verify/run_all.py

문서·스키마·토큰을 고칠 때마다 실행한다.
개수 불일치나 ID 누락은 사람이 눈으로 잡을 수 없다.

필요 패키지: sqlglot
   pip install sqlglot

OpenAPI / Mermaid / TypeScript 검증은 외부 도구가 필요해 여기 포함하지 않았다.
방법은 KUFIM_검증보고서.md §1 참조.
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

STEPS = [
    ('v1_sql_parse.py',        'DDL 구문 파싱'),
    ('v2_load_test.py',        '스키마·시드 적재 및 FK 검사'),
    ('v3_business_rules.py',   '업무 규칙 40건'),
    ('v4_affine.py',           '좌표 변환 수치 검증'),
    ('v5_docs_consistency.py', '문서 교차 정합성 10축'),
    ('v6_contrast.py',         '디자인 토큰 접근성'),
]

def main():
    verbose = '-v' in sys.argv or '--verbose' in sys.argv
    results = []

    for script, label in STEPS:
        path = os.path.join(HERE, script)
        if not os.path.exists(path):
            print('[SKIP] %-28s (파일 없음: %s)' % (label, script))
            results.append((label, 'SKIP', ''))
            continue

        proc = subprocess.run(
            [sys.executable, path],
            capture_output=True, text=True, encoding='utf-8', errors='replace',
        )
        out = (proc.stdout or '') + (proc.stderr or '')

        # 각 스크립트가 남기는 요약 줄에서 실패 건수를 읽는다
        failed = proc.returncode != 0
        for line in out.splitlines():
            if line.startswith('결과:') or line.startswith('요약:'):
                if 'FAIL 0' not in line:
                    failed = True
        if '[FAIL]' in out:
            failed = True

        status = 'FAIL' if failed else 'OK'
        summary = ''
        for line in out.splitlines():
            if line.startswith('결과:') or line.startswith('요약:') or line.startswith('결론:'):
                summary = line.strip()

        print('[%-4s] %-28s %s' % (status, label, summary))
        if verbose or failed:
            for line in out.splitlines():
                if '[FAIL]' in line or '[WARN]' in line or verbose:
                    print('        ' + line)
        results.append((label, status, summary))

    print()
    print('=' * 68)
    bad = [r for r in results if r[1] == 'FAIL']
    if bad:
        print('검증 실패 %d건' % len(bad))
        for label, _, _ in bad:
            print('  - %s' % label)
        return 1
    print('전 항목 통과 (%d개 검사)' % len([r for r in results if r[1] == 'OK']))
    return 0


if __name__ == '__main__':
    sys.exit(main())
