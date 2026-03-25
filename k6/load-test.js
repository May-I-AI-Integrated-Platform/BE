import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

// ===================== 설정 =====================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트 계정 (실제 DB에 존재하는 계정으로 변경하거나 REGISTER_USERS=true로 사전 생성)
const TEST_USERS = new SharedArray('users', function () {
  return [
    { email: 'qwer@qwer.qwer', password: 'qwer1234!', name: '최선규' },
    { email: 'asdf@asdf.asdf', password: 'qwer1234!', name: '홍길동' },
  ];
});

// ===================== 부하 시나리오 =====================
export const options = {
  scenarios: {
    // 시나리오 1: 점진적 부하 증가 (Ramp-up)
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '3m', target: 200 },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // 전체 요청의 95%가 2초 이내 응답
    http_req_duration: ['p(95)<3000'],
    // 에러율 5% 미만
    http_req_failed: ['rate<0.05'],
  },
  summaryTrendStats: ['min', 'avg', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// ===================== 헬퍼 함수 =====================

/**
 * 로그인 후 accessToken 쿠키를 반환
 */
function login(email, password) {
  const res = http.post(
    `${BASE_URL}/user/login`,
    JSON.stringify({ userEmail: email, userPassword: password }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, {
    'login 200': (r) => r.status === 200,
  });

  // 서버가 Set-Cookie로 accessToken을 내려주므로 jar에서 추출
  const cookies = res.cookies['accessToken'];
  if (!cookies || cookies.length === 0) return null;
  return cookies[0].value;
}

/**
 * accessToken 쿠키를 헤더에 포함한 공통 params 반환
 */
function authParams(token) {
  return {
    headers: { 'Content-Type': 'application/json' },
    cookies: { accessToken: token },
  };
}

// ===================== 메인 시나리오 =====================
export default function () {
  // VU마다 랜덤 계정 선택
  const user = TEST_USERS[__VU % TEST_USERS.length];

  // ── 1. 로그인 ──────────────────────────────────────────
  const token = login(user.email, user.password);
  if (!token) {
    console.warn(`[VU ${__VU}] 로그인 실패 — 스킵`);
    sleep(1);
    return;
  }

  sleep(0.5);

  // ── 2. 채팅방 생성 ────────────────────────────────────
  const createChatRes = http.post(
    `${BASE_URL}/chat`,
    JSON.stringify({ chatName: `k6-chat-${__VU}-${__ITER}` }),
    authParams(token)
  );
  check(createChatRes, {
    'chat create 200': (r) => r.status === 200,
  });

  let chatId = null;
  try {
    chatId = JSON.parse(createChatRes.body).result?.chatId;
  } catch (_) { }

  if (!chatId) {
    console.warn(`[VU ${__VU}] chatId 획득 실패 — 채팅 단계 스킵`);
    sleep(1);
    return;
  }

  sleep(0.5);

  // ── 3. 채팅방 목록 조회 ───────────────────────────────
  const listChatRes = http.get(`${BASE_URL}/chat`, authParams(token));
  check(listChatRes, {
    'chat list 200': (r) => r.status === 200,
  });

  sleep(0.5);

  // ── 4. 메시지 전송
  // GPT: 100 VU까지 정상 확인 → 200 VU 중 1/2 비율(~100 VU)만 호출
  // Claude Tier1 기준 50 RPM, VU당 ~10 req/min → 안전 동시 VU: 5개 → 200 VU 중 1/40
  const aiTypeList = [
    ...(__VU % 2 === 0 ? ['GPT'] : []),
    ...(__VU % 5 === 0 ? ['CLAUDE'] : []),
  ];
  const sendMsgRes = http.post(
    `${BASE_URL}/message`,
    JSON.stringify({
      chatId: chatId,
      aiTypeList: aiTypeList,
      text: 'k6 부하테스트 메시지입니다. 간단히 답해주세요.',
    }),
    // AI 외부 호출이 있으므로 타임아웃 넉넉히 설정
    { ...authParams(token), timeout: '60s' }
  );
  check(sendMsgRes, {
    'message send 200': (r) => r.status === 200,
  });

  sleep(0.5);

  // ── 5. 메시지 목록 조회 ──────────────────────────────
  const getMsgRes = http.get(`${BASE_URL}/message/${chatId}`, authParams(token));
  check(getMsgRes, {
    'message list 200': (r) => r.status === 200,
  });

  sleep(0.5);

  // ── 6. 유저 정보 조회 ─────────────────────────────────
  const userDataRes = http.get(`${BASE_URL}/user/data`, authParams(token));
  check(userDataRes, {
    'user data 200': (r) => r.status === 200,
  });

  sleep(0.5);

  // ── 7. 채팅방 삭제 ────────────────────────────────────
  const deleteChatRes = http.post(
    `${BASE_URL}/chat/${chatId}`,
    null,
    authParams(token)
  );
  check(deleteChatRes, {
    'chat delete 200': (r) => r.status === 200,
  });

  sleep(1);
}

// ===================== 사전 준비: 테스트 유저 등록 =====================
// k6 run --env SETUP=true load-test.js  로 실행하면 테스트 유저를 등록합니다.
export function setup() {
  if (__ENV.SETUP !== 'true') return;

  console.log('== 테스트 유저 사전 등록 ==');
  for (const u of TEST_USERS) {
    const res = http.post(
      `${BASE_URL}/user/register`,
      JSON.stringify({ userEmail: u.email, userName: u.name, userPassword: u.password }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    console.log(`register ${u.email} → ${res.status}`);
  }
}
