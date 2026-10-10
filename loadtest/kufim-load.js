// KUFIM 부하 테스트 (k6). 동시 접속 1,000명 목표(NFR) 검증용.
//
//   k6 run -e BASE_URL=http://127.0.0.1:8080 -e ADMIN_LOGIN=admin -e ADMIN_PASSWORD=... loadtest/kufim-load.js
//
// - 이용자마다 다른 X-Client-Token과 X-Forwarded-For를 보내 서로 다른 휴대폰처럼 동작합니다.
//   X-Forwarded-For는 mysql 프로필(forward-headers-strategy)에서 사설망 요청일 때만 반영됩니다.
// - 데이터: 축제 1개, 웨이팅 부스(boothId 1~3이 인기 부스), 공지·공연이 미리 있어야 합니다(loadtest/README.md).
// - 운영 서버에 돌릴 때는 짧게(축소 옵션) 돌리고, 끝나면 테스트 대기표를 정리합니다.
import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Trend } from 'k6/metrics'

const BASE = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '') + '/api/v1'
const SCALE = Number(__ENV.SCALE || 1) // 0.1이면 1/10 규모
const POPULAR_BOOTHS = [1, 2, 3]

const registered = new Counter('waiting_registered')
const rejected = new Counter('waiting_rejected')
const ticketLatency = new Trend('ticket_latency', true)
const queueLatency = new Trend('admin_queue_latency', true)

export const options = {
  // 관리자 세션 쿠키를 반복 사이에 유지합니다(k6 기본값은 반복마다 쿠키 초기화).
  noCookiesReset: true,
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      exec: 'browse',
      stages: [
        { duration: '30s', target: Math.ceil(690 * SCALE) },
        { duration: '2m', target: Math.ceil(690 * SCALE) },
        { duration: '10s', target: 0 },
      ],
    },
    waiting: {
      executor: 'ramping-vus',
      exec: 'waiting',
      stages: [
        { duration: '30s', target: Math.ceil(300 * SCALE) },
        { duration: '2m', target: Math.ceil(300 * SCALE) },
        { duration: '10s', target: 0 },
      ],
    },
    admin: {
      executor: 'constant-vus',
      exec: 'admin',
      vus: Math.max(1, Math.ceil(10 * SCALE)),
      duration: '2m40s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    ticket_latency: ['p(95)<300'],
    admin_queue_latency: ['p(95)<500'],
  },
}

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}
// 서로 다른 공인 IP(문서용 대역 대신 사설 범위 밖 임의값)를 흉내 냅니다.
function fakeIp() {
  return `11.${(__VU >> 8) & 255}.${__VU & 255}.${(__ITER % 250) + 1}`
}
function client() {
  if (!globalThis.identity) globalThis.identity = { token: uuidv4(), ip: fakeIp() }
  return globalThis.identity
}
function headers(extra = {}) {
  const { token, ip } = client()
  return { headers: { 'X-Client-Token': token, 'X-Forwarded-For': ip, ...extra } }
}
const ok = (res, name) => check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 })

// 홈 → 공연 → 부스 → 공지를 둘러보는 일반 이용자 (첫 화면 로드 6개 요청 + 상세)
export function browse() {
  const h = headers()
  const home = http.batch([
    ['GET', `${BASE}/festival`, null, h],
    ['GET', `${BASE}/booths`, null, h],
    ['GET', `${BASE}/facilities`, null, h],
    ['GET', `${BASE}/stages`, null, h],
    ['GET', `${BASE}/performances`, null, h],
    ['GET', `${BASE}/notices`, null, h],
  ])
  home.forEach((r, i) => ok(r, ['festival', 'booths', 'facilities', 'stages', 'performances', 'notices'][i]))
  sleep(2 + Math.random() * 3)
  ok(http.get(`${BASE}/booths/${1 + Math.floor(Math.random() * 20)}`, h), 'booth detail')
  ok(http.get(`${BASE}/booths?sort=popular&limit=3&waitingOnly=true`, h), 'popular')
  sleep(3 + Math.random() * 5)
}

// 인기 부스에 접수한 뒤 대기표를 폴링(웹소켓이 끊긴 최악의 경우: 10초 간격)
export function waiting() {
  const id = client()
  if (!id.waitingId && !id.done) {
    const boothId = POPULAR_BOOTHS[__VU % POPULAR_BOOTHS.length]
    const res = http.post(
      `${BASE}/booths/${boothId}/waitings`,
      JSON.stringify({ studentNumber: '202600000', phoneNumber: '01000000000', partySize: 2, privacyConsent: true }),
      headers({ 'Content-Type': 'application/json', 'Idempotency-Key': uuidv4() }),
    )
    if (res.status === 201) {
      registered.add(1)
      id.waitingId = res.json('data.waitingId')
      id.boothId = boothId
    } else {
      rejected.add(1)
      id.done = true
    }
    ok(res, 'register')
  }
  if (id.waitingId) {
    const me = http.get(`${BASE}/waitings/me`, headers())
    ticketLatency.add(me.timings.duration)
    ok(me, 'my ticket')
    ok(http.get(`${BASE}/booths/${id.boothId}/waiting-status`, headers()), 'waiting status')
  }
  sleep(10)
}

// 인기 부스 대기 명단을 5초마다 새로고침하는 운영자
export function admin() {
  if (!globalThis.loggedIn) {
    const res = http.post(
      `${BASE}/admin/auth/login`,
      JSON.stringify({ loginId: __ENV.ADMIN_LOGIN || 'admin', password: __ENV.ADMIN_PASSWORD }),
      headers({ 'Content-Type': 'application/json' }),
    )
    globalThis.loggedIn = ok(res, 'admin login')
    if (!globalThis.loggedIn) return sleep(5)
  }
  const boothId = POPULAR_BOOTHS[__VU % POPULAR_BOOTHS.length]
  const res = http.get(`${BASE}/admin/booths/${boothId}/waitings`, headers())
  queueLatency.add(res.timings.duration)
  ok(res, 'admin queue')
  sleep(5)
}
