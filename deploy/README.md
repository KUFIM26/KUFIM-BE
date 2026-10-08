# KUFIM 무료 배포 가이드

서버 1대에 **MySQL · Redis · 백엔드 · Caddy(프론트 + HTTPS)** 를 Docker Compose로 올립니다. 모든 구성 요소는 무료이며, 아래 순서를 지키면 **비용이 발생하지 않습니다.**

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
| Oracle Cloud **Always Free** | 기간 제한 없이 무료인 VM(ARM 최대 4코어·24GB). AWS 프리 티어는 기간·크레딧 제한이 있어 축제 후 과금 위험이 있습니다. |
| DuckDNS 도메인 | 무료 고정 서브도메인. **QR에 주소가 인쇄되므로** 주소가 바뀌면 안 됩니다. |
| Caddy 자동 HTTPS | Let's Encrypt 인증서를 무료로 자동 발급·갱신합니다. |

## 비용 주의 사항 (반드시 읽기)

- Oracle 가입 시 카드 인증이 있지만 **Free Tier 계정은 유료 리소스를 만들 수 없어** 청구되지 않습니다. **"Upgrade to Pay As You Go"를 누르지 마세요.**
- 인스턴스·볼륨은 **"Always Free-eligible"** 표시가 있는 것만 만듭니다. 부트 볼륨 합계 200GB 이하, 블록 볼륨·로드 밸런서·Object Storage 유료 등급은 만들지 않습니다.
- Always Free 인스턴스는 7일간 사용률이 매우 낮으면 회수될 수 있습니다. 축제 1~2주 전에 만들고, 축제 기간에는 트래픽이 있으므로 문제없습니다.
- 문자(SMS)는 아직 공급자가 연결되지 않아 **발송 비용이 0원**입니다(`smsStatus=NOT_CONFIGURED`). 공급자를 연결하는 시점부터 비용이 생깁니다(TBD-07).

## 1. 서버 준비 (Oracle Cloud, 무료)

1. Oracle Cloud 가입 → 홈 리전은 **서울** 또는 **춘천**.
2. Compute → Instances → Create:
   - Image: **Ubuntu 22.04/24.04**
   - Shape: **VM.Standard.A1.Flex (Ampere ARM)** — 2 OCPU / 12GB 권장 (Always Free 범위 내)
     - "Out of capacity"가 뜨면 잠시 후 재시도하거나 **VM.Standard.E2.1.Micro**(1GB, 무료)를 사용합니다. 1GB에서는 아래 "메모리가 작은 서버" 항목을 적용하세요.
   - SSH 키 등록, 공인 IP 할당.
3. Networking → 해당 VCN의 Security List에 **Ingress TCP 80, 443** 추가.
4. 서버에서 방화벽 열기와 Docker 설치:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # 다시 로그인
```

## 2. 도메인 (DuckDNS, 무료)

1. https://www.duckdns.org 에서 로그인 → 서브도메인 생성(예: `kufim`) → **current ip**에 서버 공인 IP 입력.
2. 주소는 `kufim.duckdns.org`가 됩니다. 이 값이 QR에 인쇄되므로 **확정 후 바꾸지 않습니다.**

## 3. 배포

```bash
mkdir -p ~/kufim && cd ~/kufim
git clone https://github.com/KUFIM26/KUFIM-BE.git
git clone https://github.com/KUFIM26/KUFIM-FE.git
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

## 메모리가 작은 서버 (1GB)

`deploy/docker-compose.override.yml`을 아래 내용으로 만들면 `docker compose`가 자동으로 합쳐 적용합니다. 스왑도 만듭니다.

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

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 검증된 것과 아닌 것

- **로컬에서 검증함:** 같은 구성(MySQL 8.0.46 · Redis · `mysql` 프로필 jar · 이 `Caddyfile`)으로 Flyway V1~V5 적용, utf8mb4 한글 저장, Redis 세션 유지, Secure/HttpOnly 세션 쿠키, 프록시 뒤 이용자별 IP 제한, `/ws` WebSocket 업그레이드와 실시간 호출·공지, SPA 경로(`/w/{boothCode}`) 처리.
- **아직 검증하지 못함:** Docker 이미지 빌드 자체(검증 환경에 Docker가 없었음), ARM 서버에서의 실행, 실제 도메인의 HTTPS 인증서 발급, 동시 접속 부하(k6, 9주차 계획).
