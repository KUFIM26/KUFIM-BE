/**
 * KUFIM 부하 테스트 시나리오 (k6)
 *
 * 대응 요구사항
 *   NFR-P-001  API 평균 응답 200ms 이하
 *   NFR-P-002  동시 접속 1,000명 이상에서 무중단
 *   NFR-P-005  트래픽 폭주 시나리오 통과
 *   NFR-R-001  가용성 99.5% 이상
 *
 * 실행
 *   k6 run -e BASE_URL=https://<domain>/api/v1 k6-scenarios.js
 *   k6 run -e SCENARIO=spike k6-scenarios.js          # 특정 시나리오만
 *
 * 주의
 *   - 반드시 dev 환경에 대해 실행한다. prod 실행 금지.
 *   - 웨이팅 등록은 실제 데이터를 만든다. 실행 후 dev DB 를 초기화할 것.
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { randomIntBetween, uuidv4 } from './helpers.js';

// ---------------------------------------------------------------- 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const ONLY = __ENV.SCENARIO || '';

// 부스 코드는 dev 시드 데이터 기준 (seed_dev.sql)
const BOOTH_IDS = [1, 2, 4, 5, 6];
const BOOTH_CODES = ['DEVCODE00001', 'DEVCODE00002', 'DEVCODE00004', 'DEVCODE00005', 'DEVCODE00006'];

// ---------------------------------------------------------------- 지표
const browseLatency = new Trend('kufim_browse_latency', true);
const waitingLatency = new Trend('kufim_waiting_register_latency', true);
const congestionLatency = new Trend('kufim_congestion_latency', true);
const waitingSuccess = new Rate('kufim_waiting_register_success');
const duplicateBlocked = new Counter('kufim_duplicate_blocked');
const closedBooth = new Counter('kufim_closed_booth');

// ---------------------------------------------------------------- 임계값
export const options = {
  thresholds: {
    // NFR-P-001: 평균 200ms. p(95) 도 함께 본다 (평균만 보면 롱테일을 놓친다)
    'http_req_duration': ['avg<200', 'p(95)<500'],
    'kufim_browse_latency': ['avg<200', 'p(95)<400'],
    'kufim_congestion_latency': ['avg<200', 'p(95)<400'],
    // 웨이팅 등록은 Redis 원자 연산이 걸려 있어 조회보다 여유를 둔다
    'kufim_waiting_register_latency': ['avg<300', 'p(95)<800'],
    // NFR-R-001: 가용성 99.5% -> 실패율 0.5% 미만
    'http_req_failed': ['rate<0.005'],
    'checks': ['rate>0.99'],
  },

  scenarios: buildScenarios(),
};

function buildScenarios() {
  const all = {
    // 1) 기본 부하: 축제 중 평상시 조회 트래픽
    browse: {
      executor: 'ramping-vus',
      exec: 'browseFlow',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 200 },
        { duration: '3m', target: 600 },
        { duration: '5m', target: 1000 },   // NFR-P-002 목표치
        { duration: '2m', target: 1000 },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
      tags: { scenario: 'browse' },
    },

    // 2) 웨이팅 등록 집중: 부스 오픈 직후 몰리는 상황
    waiting: {
      executor: 'ramping-arrival-rate',
      exec: 'waitingFlow',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 600,
      stages: [
        { duration: '1m', target: 20 },
        { duration: '3m', target: 60 },    // 초당 60건 등록
        { duration: '2m', target: 60 },
        { duration: '1m', target: 5 },
      ],
      tags: { scenario: 'waiting' },
    },

    // 3) 스파이크: 인기 가수 무대 직전 동시 유입
    spike: {
      executor: 'ramping-vus',
      exec: 'spikeFlow',
      startVUs: 50,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '20s', target: 1200 },  // 급증
        { duration: '1m', target: 1200 },
        { duration: '30s', target: 50 },
      ],
      gracefulRampDown: '20s',
      tags: { scenario: 'spike' },
    },

    // 4) 관리자 운영: 호출·혼잡도 입력이 지속 발생
    admin: {
      executor: 'constant-vus',
      exec: 'adminFlow',
      vus: 10,
      duration: '5m',
      tags: { scenario: 'admin' },
    },
  };

  if (ONLY) {
    if (!all[ONLY]) throw new Error(`알 수 없는 시나리오: ${ONLY}`);
    return { [ONLY]: all[ONLY] };
  }
  return all;
}

// ---------------------------------------------------------------- 공통
function clientHeaders(token) {
  return {
    headers: {
      'X-Client-Token': token,
      'Accept-Language': 'ko',
      'Content-Type': 'application/json',
    },
  };
}

function pick(arr) {
  return arr[randomIntBetween(0, arr.length - 1)];
}

// ---------------------------------------------------------------- 1) 조회
export function browseFlow() {
  const token = uuidv4();
  const h = clientHeaders(token);

  group('홈 진입', () => {
    const res = http.batch([
      ['GET', `${BASE_URL}/festival`, null, h],
      ['GET', `${BASE_URL}/stages`, null, h],
      ['GET', `${BASE_URL}/notices?urgentOnly=true`, null, h],
      ['GET', `${BASE_URL}/performances/now`, null, h],
    ]);
    res.forEach((r) => browseLatency.add(r.timings.duration));
    check(res[0], { '축제 정보 200': (r) => r.status === 200 });
    check(res[1], {
      '무대 혼잡도 200': (r) => r.status === 200,
      '혼잡도 응답에 serverTime 포함': (r) => {
        try { return !!r.json('data.serverTime'); } catch (e) { return false; }
      },
    });
  });
  sleep(randomIntBetween(1, 3));

  group('부스 탐색', () => {
    const r = http.get(`${BASE_URL}/booths`, h);
    browseLatency.add(r.timings.duration);
    check(r, { '부스 목록 200': (x) => x.status === 200 });
  });
  sleep(randomIntBetween(1, 4));

  group('지도', () => {
    const res = http.batch([
      ['GET', `${BASE_URL}/map`, null, h],
      ['GET', `${BASE_URL}/map/pins`, null, h],
    ]);
    res.forEach((r) => browseLatency.add(r.timings.duration));
    check(res[1], { '지도 핀 200': (r) => r.status === 200 });
  });
  sleep(randomIntBetween(2, 5));
}

// ---------------------------------------------------------------- 2) 웨이팅
export function waitingFlow() {
  const token = uuidv4();
  const h = clientHeaders(token);
  const code = pick(BOOTH_CODES);

  // QR 스캔 -> 부스 해석
  const resolve = http.get(`${BASE_URL}/booths/code/${code}`, h);
  check(resolve, { 'QR 해석 200': (r) => r.status === 200 });
  if (resolve.status !== 200) return;

  let boothId;
  try { boothId = resolve.json('data.boothId'); } catch (e) { return; }
  sleep(1);

  // 대기 현황 확인
  const status = http.get(`${BASE_URL}/booths/${boothId}/waiting-status`, h);
  browseLatency.add(status.timings.duration);
  sleep(randomIntBetween(1, 3));

  // 등록
  const payload = JSON.stringify({
    partySize: randomIntBetween(1, 6),
    privacyConsent: true,
  });
  const reg = http.post(`${BASE_URL}/booths/${boothId}/waitings`, payload, h);
  waitingLatency.add(reg.timings.duration);

  const ok = reg.status === 201;
  waitingSuccess.add(ok);
  if (reg.status === 409) {
    let code409 = '';
    try { code409 = reg.json('error.code'); } catch (e) { /* noop */ }
    if (code409 === 'W001') duplicateBlocked.add(1);
    if (code409 === 'W002' || code409 === 'W005') closedBooth.add(1);
  }
  check(reg, {
    '등록 201 또는 정상 거부 409': (r) => r.status === 201 || r.status === 409,
    '등록 성공 시 순번 포함': (r) => {
      if (r.status !== 201) return true;
      try { return typeof r.json('data.aheadTeamCount') === 'number'; } catch (e) { return false; }
    },
  });
  if (!ok) return;

  // 대기표 폴링 (WebSocket 폴백 경로를 부하로 재현)
  let waitingId;
  try { waitingId = reg.json('data.waitingId'); } catch (e) { return; }

  for (let i = 0; i < 5; i += 1) {
    sleep(randomIntBetween(3, 6));
    const poll = http.get(`${BASE_URL}/waitings/${waitingId}`, h);
    browseLatency.add(poll.timings.duration);
    check(poll, { '대기표 조회 200': (r) => r.status === 200 });
  }

  // 일부는 취소 (실제 이용 패턴 반영)
  if (randomIntBetween(1, 10) <= 3) {
    const del = http.del(`${BASE_URL}/waitings/${waitingId}`, null, h);
    check(del, { '취소 204': (r) => r.status === 204 });
  }
}

// ---------------------------------------------------------------- 3) 스파이크
export function spikeFlow() {
  const token = uuidv4();
  const h = clientHeaders(token);

  // 공연 직전에는 무대 혼잡도와 타임테이블에 집중된다
  const res = http.batch([
    ['GET', `${BASE_URL}/stages`, null, h],
    ['GET', `${BASE_URL}/performances/now`, null, h],
    ['GET', `${BASE_URL}/notices?urgentOnly=true`, null, h],
  ]);
  res.forEach((r) => congestionLatency.add(r.timings.duration));
  check(res[0], { '스파이크 중 혼잡도 200': (r) => r.status === 200 });
  sleep(randomIntBetween(1, 2));
}

// ---------------------------------------------------------------- 4) 관리자
export function adminFlow() {
  const loginRes = http.post(
    `${BASE_URL}/admin/auth/login`,
    JSON.stringify({ loginId: __ENV.ADMIN_ID || 'admin', password: __ENV.ADMIN_PW || 'kufim1234' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (!check(loginRes, { '관리자 로그인 200': (r) => r.status === 200 })) {
    sleep(5);
    return;
  }

  let accessToken;
  try { accessToken = loginRes.json('data.accessToken'); } catch (e) { return; }
  const ah = {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
  };

  group('혼잡도 입력', () => {
    const zoneId = randomIntBetween(1, 5);
    const levels = ['SMOOTH', 'NORMAL', 'CROWDED', 'VERY_CROWDED'];
    const body = JSON.stringify({
      level: pick(levels),
      estimatedPeople: randomIntBetween(30, 800),
    });
    const r = http.post(`${BASE_URL}/admin/zones/${zoneId}/congestion`, body, ah);
    congestionLatency.add(r.timings.duration);
    check(r, { '혼잡도 입력 201': (x) => x.status === 201 });
  });
  sleep(randomIntBetween(20, 40));

  group('대기 호출', () => {
    const boothId = pick(BOOTH_IDS);
    const list = http.get(`${BASE_URL}/admin/booths/${boothId}/waitings`, ah);
    check(list, { '대기 목록 200 또는 403': (r) => r.status === 200 || r.status === 403 });

    const call = http.post(`${BASE_URL}/admin/booths/${boothId}/waitings/call-next`, null, ah);
    check(call, {
      '호출 200 또는 대기없음 409': (r) => r.status === 200 || r.status === 409 || r.status === 403,
    });
  });
  sleep(randomIntBetween(10, 20));

  group('현황판', () => {
    const r = http.get(`${BASE_URL}/admin/dashboard`, ah);
    check(r, { '현황판 200 또는 403': (x) => x.status === 200 || x.status === 403 });
  });
  sleep(randomIntBetween(5, 15));
}

// ---------------------------------------------------------------- 요약
export function handleSummary(data) {
  const m = data.metrics;
  const val = (name, key) => {
    const x = m[name];
    if (!x || !x.values) return 'n/a';
    const v = x.values[key];
    return typeof v === 'number' ? v.toFixed(2) : 'n/a';
  };

  const lines = [
    '',
    '='.repeat(64),
    'KUFIM 부하 테스트 결과',
    '='.repeat(64),
    `총 요청          : ${m.http_reqs ? m.http_reqs.values.count : 'n/a'}`,
    `실패율           : ${val('http_req_failed', 'rate')}   (기준 < 0.005)`,
    `평균 응답        : ${val('http_req_duration', 'avg')} ms   (NFR-P-001 기준 < 200)`,
    `p95 응답         : ${val('http_req_duration', 'p(95)')} ms`,
    `p99 응답         : ${val('http_req_duration', 'p(99)')} ms`,
    '',
    `조회 평균        : ${val('kufim_browse_latency', 'avg')} ms`,
    `혼잡도 평균      : ${val('kufim_congestion_latency', 'avg')} ms`,
    `웨이팅 등록 평균 : ${val('kufim_waiting_register_latency', 'avg')} ms`,
    `웨이팅 등록 성공 : ${val('kufim_waiting_register_success', 'rate')}`,
    `중복 등록 차단   : ${m.kufim_duplicate_blocked ? m.kufim_duplicate_blocked.values.count : 0} 건`,
    `마감 부스 거부   : ${m.kufim_closed_booth ? m.kufim_closed_booth.values.count : 0} 건`,
    '='.repeat(64),
    '',
  ];

  return {
    stdout: lines.join('\n'),
    'k6-summary.json': JSON.stringify(data, null, 2),
  };
}
