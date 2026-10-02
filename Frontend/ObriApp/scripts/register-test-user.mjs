#!/usr/bin/env node
// 로컬 수동 테스트용 — 테스트 Firebase 계정을 백엔드에 등록(profile 생성)한다.
// 로컬 백엔드가 ddl-auto=create라 재기동마다 DB가 초기화되므로, 재기동 후 앱에서
// 로그인만으로는 프로필이 없어 401이 난다. 이 스크립트로 먼저 등록해두면 바로 테스트 가능.
//
// 사용법 (Frontend/ObriApp 디렉토리에서):
//   TEST_EMAIL=... TEST_PASSWORD=... npm run register-test-user
// .env의 EXPO_PUBLIC_API_URL이 실기기용 LAN IP일 수 있으니, 로컬(웹/시뮬레이터) 테스트 중이면
//   API_URL=http://localhost:8080 TEST_EMAIL=... TEST_PASSWORD=... npm run register-test-user
// 로 오버라이드할 것.
import { initializeApp } from "firebase/app";
import { getAuth, signInWithEmailAndPassword } from "firebase/auth";

const email = process.env.TEST_EMAIL;
const password = process.env.TEST_PASSWORD;
const apiUrl = process.env.API_URL || process.env.EXPO_PUBLIC_API_URL;

if (!email || !password) {
  console.error("TEST_EMAIL / TEST_PASSWORD 환경변수가 필요합니다.");
  process.exit(1);
}

const firebaseConfig = {
  apiKey: process.env.EXPO_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.EXPO_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.EXPO_PUBLIC_FIREBASE_APP_ID,
};

async function main() {
  const app = initializeApp(firebaseConfig);
  const auth = getAuth(app);
  const cred = await signInWithEmailAndPassword(auth, email, password);
  const idToken = await cred.user.getIdToken();

  const res = await fetch(`${apiUrl}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${idToken}` },
    body: JSON.stringify({
      nickname: "테스트유저",
      phoneNumber: "010-0000-0000",
      instrument: "바이올린",
      careers: [],
    }),
  });
  const body = await res.json().catch(() => null);

  if (res.status === 200) {
    console.log("✅ 등록 완료 — 이제 앱에서 이 계정으로 로그인하면 프로필이 있는 상태입니다.");
  } else if (res.status === 409) {
    console.log("ℹ️  이미 등록된 계정입니다 — 그대로 로그인하면 됩니다.");
  } else {
    console.log(`❌ 등록 실패 status=${res.status} message=${body?.message}`);
    process.exit(1);
  }
}

main().catch((err) => {
  console.error("실패:", err.message);
  process.exit(1);
});
