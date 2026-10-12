/**
 * k6 시나리오 보조 함수.
 *
 * k6 는 Node 런타임이 아니므로 crypto / Math.random 외부 라이브러리를 쓸 수 없다.
 * jslib(k6/x) 의존을 피하려고 필요한 것만 직접 구현한다.
 */

/** min 이상 max 이하의 정수 (양끝 포함) */
export function randomIntBetween(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * RFC 4122 v4 형식 UUID.
 * 익명 토큰(X-Client-Token)으로 사용한다.
 * 암호학적 강도가 필요 없는 부하 테스트 전용이다.
 */
export function uuidv4() {
  const hex = '0123456789abcdef';
  let out = '';
  for (let i = 0; i < 36; i += 1) {
    if (i === 8 || i === 13 || i === 18 || i === 23) {
      out += '-';
    } else if (i === 14) {
      out += '4';
    } else if (i === 19) {
      out += hex[(Math.floor(Math.random() * 4)) + 8];
    } else {
      out += hex[Math.floor(Math.random() * 16)];
    }
  }
  return out;
}

/** 배열에서 임의 원소 */
export function pickOne(arr) {
  return arr[randomIntBetween(0, arr.length - 1)];
}
