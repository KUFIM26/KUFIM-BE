# 산출물 검증 스크립트

문서·스키마·디자인 토큰을 고칠 때마다 실행한다.
개수 불일치, ID 누락, 링크 깨짐은 사람이 눈으로 잡을 수 없다.

## 실행

```bash
pip install sqlglot
python docs/verify/run_all.py          # 요약만
python docs/verify/run_all.py -v       # 전체 출력
```

개별 실행도 가능하다.

```bash
python docs/verify/v5_docs_consistency.py
```

## 검사 목록

| 스크립트 | 검사 | 언제 다시 돌리나 |
|---|---|---|
| `v1_sql_parse.py` | DDL MySQL 구문 파싱 | 스키마 수정 시 |
| `v2_load_test.py` | 스키마·시드 실제 적재, FK 위반 | 스키마·시드 수정 시 |
| `v3_business_rules.py` | enum·상태 정합성·다형 참조 등 40건 | 시드·상태 머신 수정 시 |
| `v4_affine.py` | 좌표 변환 행렬 성립·축척 타당성 | 기준점 좌표 변경 시 |
| `v5_docs_consistency.py` | FR 커버리지·개수·링크·enum 일관성 10축 | **문서 수정 시 항상** |
| `v6_contrast.py` | WCAG 명도 대비·색각 시뮬레이션 | `design/tokens.css` 수정 시 |

## 여기 없는 검증

외부 도구가 필요해 `run_all.py` 에 포함하지 않았다.

```bash
# OpenAPI 3.1 스펙 검증
pip install openapi-spec-validator
python -c "import yaml;from openapi_spec_validator import validate;validate(yaml.safe_load(open('docs/openapi/kufim-openapi.yaml',encoding='utf-8')))"

# TypeScript 타입 생성 및 컴파일 검증
npx openapi-typescript docs/openapi/kufim-openapi.yaml -o docs/openapi/kufim-api.d.ts
npx tsc --noEmit --strict --skipLibCheck docs/openapi/kufim-api.d.ts

# Mermaid 다이어그램 렌더링 검증
npx @mermaid-js/mermaid-cli -i <다이어그램.mmd> -o out.svg

# k6 스크립트 문법 검증
npx acorn --ecma2022 --module docs/loadtest/k6-scenarios.js > /dev/null
```

## 결과

`KUFIM_검증보고서.md` 에 최근 실행 결과와 발견된 결함 이력이 정리되어 있다.
