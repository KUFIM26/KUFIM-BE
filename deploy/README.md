# KUFIM 무료 배포 가이드

AWS EC2 서버 1대에 **MySQL · Redis · 백엔드 · Caddy(프론트 + HTTPS)** 를 Docker Compose로 올립니다. AWS **Free 플랜** 계정의 크레딧 안에서 운영하므로, 아래 순서를 지키면 **청구 금액이 발생하지 않습니다.**

```
이용자/관리자 ──HTTPS──> Caddy :443 ─┬─ /            → 프론트 정적 파일 (KUFIM-FE, API 모드 빌드)
                                     ├─ /api/*       → backend:8080 (Spring, mysql 프로필)
                                     └─ /ws          → backend:8080 (STOMP WebSocket)
                                                         ├─ mysql:3306 (데이터)
                                                         └─ redis:6379 (관리자 세션 · 요청 제한)
```

## 왜 이 구성인가

| 결정 | 이유 |
|---|---|
| 프론트와 API를 **같은 주소**에서 제공 | 관리자 세션 쿠키가 `SameSite=Lax`이고 XSRF 쿠키를 프론트가 읽어야 합니다. 다른 도메인에 두면 관리자 기능이 동작하지 않습니다. |
| Vercel 대신 Caddy | Vercel rewrite는 **WebSocket을 프록시하지 못해** 실시간 호출이 동작하지 않습니다. Vercel은 데모(mock) 용도로만 유지합니다. |
| AWS **Free 플랜** + EC2 | 계획서의 인프라(AWS)와 일치. Free 플랜은 크레딧($100 + 최대 $100)을 다 쓰거나 6개월이 지나면 계정이 닫힐 뿐 **청구되지 않습니다.** 축제(11주차)까지 충분합니다. |
| RDS 대신 MySQL 컨테이너 | RDS를 따로 쓰면 크레딧이 더 빨리 줄어듭니다. 한 서버에 함께 두고 백업은 `mysqldump`로 합니다. |
| DuckDNS 도메인 | 무료 고정 서브도메인. **QR에 주소가 인쇄되므로** 주소가 바뀌면 안 됩니다. |
| Caddy 자동 HTTPS | Let's Encrypt 인증서를 무료로 자동 발급·갱신합니다. |

## 비용 주의 사항 (반드시 읽기)

AWS 공식 기준(2025-07-15 이후 가입 계정): 가입 시 $100 크레딧, 활동 완료 시 최대 $100 추가. **Free 플랜은 6개월 또는 크레딧 소진 중 먼저 오는 시점에 계정이 자동 종료되며, Paid 플랜으로 전환하지 않는 한 청구되지 않습니다.** 사용한 서버·디스크·공인 IP 요금은 크레딧에서 차감됩니다.

- 가입할 때 **"Free plan"** 을 선택합니다. **"Upgrade to paid plan" / "Paid plan"은 누르지 않습니다.** 전환하면 크레딧 이후 사용량이 카드로 청구됩니다.
- 인스턴스는 콘솔에서 **"Free tier eligible"** 표시가 있는 유형만 고릅니다: `t3.micro`, `t3.small`, `t4g.micro`, `t4g.small`, `c7i-flex.large`, `m7i-flex.large`.
- 크레딧 잔액은 **Billing and Cost Management → Credits**에서 매주 확인합니다. 공인 IPv4는 시간당 $0.005(월 약 $3.6)가 크레딧에서 나갑니다.
- **6개월 뒤(또는 크레딧 소진 시) 계정이 닫히면 90일 후 데이터가 삭제됩니다.** 축제가 끝나면 보고서용 데이터를 반드시 `mysqldump`로 PC에 받아 둡니다(아래 5번).
- 문자(SMS)는 아직 공급자가 연결되지 않아 **발송 비용이 0원**입니다(`smsStatus=NOT_CONFIGURED`). 공급자를 연결하는 시점부터 별도 비용이 생깁니다(TBD-07).

### 서버 크기 계획 (크레딧 절약)

| 시기 | 인스턴스 | 메모리 | 비고 |
|---|---|---|---|
| 준비·리허설 | `t3.small` | 2GB | 평소 운영. "메모리가 작은 서버" 설정과 스왑 적용 |
| 축제 당일 | `m7i-flex.large` | 8GB | 축제 전날 **중지 → 유형 변경 → 시작**으로 키우고, 끝나면 다시 `t3.small`로 줄입니다 |

둘 다 x86이라 같은 디스크로 유형만 바꿀 수 있습니다(ARM `t4g`와는 섞어 쓸 수 없음). 요금은 리전·시점마다 다르니 바꾸기 전 [EC2 요금](https://aws.amazon.com/ec2/pricing/on-demand/)에서 프로젝트 리전(시드니) 시간당 가격을 확인하고, Credits 잔액으로 남은 기간을 계산하세요.

## 1. 서버 준비 (AWS EC2)

1. AWS 가입 → **Free plan** 선택 → 콘솔 우측 상단 리전을 **프로젝트 리전**으로 맞춥니다. 새 AWS 경험(소셜 로그인으로 가입한 프로젝트)은 연락처 주소로 정해진 **리전 하나에만** 리소스를 만들 수 있습니다(AWS Settings → View all projects → Overview → Additional Info → Region). KUFIM 프로젝트는 **아시아 태평양(시드니) ap-southeast-2**입니다. 한국에서 접속 지연은 약 0.1~0.15초로 웨이팅 서비스에는 문제가 없습니다.
2. **보안 먼저:** 루트 계정에 MFA를 켭니다(IAM → 보안 자격 증명). 공용 PC에서 로그인하지 않습니다.
3. EC2 → 인스턴스 시작:
   - 이름: `kufim`
   - AMI: **Ubuntu Server 24.04 LTS (x86)** — "Free tier eligible" 표시 확인
   - 인스턴스 유형: **t3.small** ("Free tier eligible")
   - 키 페어: 새로 생성(.pem 다운로드, 잃어버리면 접속 불가)
   - 네트워크 설정 → 보안 그룹 생성:
     - SSH(22): **내 IP**만
     - HTTP(80), HTTPS(443): 0.0.0.0/0
   - 스토리지: **gp3 20GiB**
4. EC2 → **탄력적 IP** → 할당 → 방금 만든 인스턴스에 연결합니다. 중지·시작해도 IP가 바뀌지 않아 도메인을 다시 맞출 필요가 없습니다. (연결된 IP도 크레딧에서 시간당 $0.005 차감)
5. 접속 후 Docker 설치와 스왑(2GB) 추가:

```bash
ssh -i kufim.pem ubuntu@<탄력적 IP>
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && exit   # 다시 접속하면 적용
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Ubuntu AMI는 OS 방화벽이 기본으로 꺼져 있어 보안 그룹만 열면 됩니다.

## 2. 도메인 (DuckDNS, 무료)

1. https://www.duckdns.org 에서 로그인 → 서브도메인 생성(예: `kufim`) → **current ip**에 **탄력적 IP** 입력.
2. 주소는 `kufim.duckdns.org`가 됩니다. 이 값이 QR에 인쇄되므로 **확정 후 바꾸지 않습니다.**

## 3. 배포

```bash
mkdir -p ~/kufim && cd ~/kufim
# 배포 파일과 실시간 기능은 main에 머지되기 전까지 JGN_BE_APPLY 브랜치에 있습니다.
git clone -b JGN_BE_APPLY https://github.com/KUFIM26/KUFIM-BE.git
git clone -b JGN_BE_APPLY https://github.com/KUFIM26/KUFIM-FE.git
cd KUFIM-BE/deploy
cp .env.example .env
nano .env        # SITE_DOMAIN, 비밀번호 3개, 첫 관리자 ID/비밀번호 입력
docker compose up -d --build
docker compose logs -f backend   # "Started KufimBeApplication" 확인
```

- 비밀번호는 `openssl rand -base64 24`로 만듭니다.
- `https://SITE_DOMAIN/admin/login`에서 첫 관리자로 로그인되는지 확인한 뒤, `.env`의 `KUFIM_ADMIN_LOGIN`·`KUFIM_ADMIN_PASSWORD`를 **비우고** `docker compose up -d`를 다시 실행합니다.
- 이후 관리자 화면 또는 `README`의 순서대로 축제 → (도면) → 무대 → 공연 → 부스를 등록합니다. 부스를 등록하면 QR 코드가 자동 발급됩니다.

## 4. 업데이트

```bash
cd ~/kufim/KUFIM-BE && git pull && cd ../KUFIM-FE && git pull
cd ../KUFIM-BE/deploy && docker compose up -d --build
```

이미 적용된 Flyway 마이그레이션(V1~V5)은 수정하지 말고 새 버전 파일로 추가합니다.

## 5. 백업 (무료, 서버 내부)

```bash
mkdir -p ~/kufim/backups
docker compose exec -T mysql sh -c 'mysqldump -ukufim -p"$MYSQL_PASSWORD" --single-transaction kufim' \
  | gzip > ~/kufim/backups/kufim-$(date +%F-%H%M).sql.gz
```

축제 기간에는 `crontab -e`에 `0 * * * *`로 등록해 1시간마다 백업하고, 개인 PC로 주기적으로 내려받습니다(`scp`).

## 6. 운영 확인 목록

| 항목 | 확인 방법 |
|---|---|
| 서버 상태 | `curl https://SITE_DOMAIN/api/v1/status` → `UP` |
| QR 주소 | 관리자 → 부스 설정의 QR을 휴대폰 기본 카메라로 스캔 → 접수 화면 |
| 실시간 | 관리자 대기 명단 상단에 **"실시간 연결됨"** 표시 |
| 이용자 IP 제한 | `.env`의 `WAITING_PER_IP_PER_MINUTE`(기본 300). 캠퍼스 와이파이·통신사 NAT 뒤의 이용자는 같은 IP를 공유하므로 축제 당일 너무 낮게 두지 않습니다. |

## 메모리가 작은 서버 (t3.small 2GB 이하)

`deploy/docker-compose.override.yml`을 아래 내용으로 만들면 `docker compose`가 자동으로 합쳐 적용합니다. 축제 당일 큰 인스턴스로 바꿀 때는 이 파일을 지우고(`docker compose up -d`) 기본값으로 돌립니다.

```yaml
services:
  mysql:
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
      - --default-time-zone=+09:00
      - --performance-schema=OFF
      - --innodb-buffer-pool-size=128M
  backend:
    environment:
      JAVA_TOOL_OPTIONS: -Xmx384m
```

## 축제 후 정리

1. `mysqldump` 백업을 PC로 받습니다(보고서·데이터 분석용).
2. EC2 → 인스턴스 **종료(Terminate)**, **탄력적 IP 릴리스**. 남겨 두면 크레딧이 계속 줄어듭니다.
3. Free 플랜 계정은 6개월 뒤 자동으로 닫히며 청구되지 않습니다.

## 검증된 것과 아닌 것

- **로컬에서 검증함:** 같은 구성(MySQL 8.0.46 · Redis · `mysql` 프로필 jar · 이 `Caddyfile`)으로 Flyway V1~V5 적용, utf8mb4 한글 저장, Redis 세션 유지, Secure/HttpOnly 세션 쿠키, 프록시 뒤 이용자별 IP 제한, `/ws` WebSocket 업그레이드와 실시간 호출·공지, SPA 경로(`/w/{boothCode}`) 처리.
- **아직 검증하지 못함:** Docker 이미지 빌드 자체(검증 환경에 Docker가 없었음), EC2에서의 실행, 실제 도메인의 HTTPS 인증서 발급, 동시 접속 부하(k6, 9주차 계획).
