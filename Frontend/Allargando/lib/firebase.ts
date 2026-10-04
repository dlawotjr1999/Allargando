// Firebase 클라이언트 SDK 초기화 (Expo Go는 네이티브 모듈을 못 쓰므로 firebase JS SDK 사용).
// AsyncStorage로 인증 세션을 영속화 — 없으면 앱 재시작마다 로그아웃 상태로 돌아간다.
import { initializeApp } from "firebase/app";
// @firebase/auth(v12.18.0)의 exports map은 "types" 키가 "react-native" 키보다 먼저 와서
// tsc가 customConditions(["react-native"])와 무관하게 항상 일반 빌드 타입을 집는다 —
// 런타임(Metro)은 react-native 조건을 정확히 타므로 실제로는 문제없이 동작함. 타입만 억제.
// @ts-expect-error firebase/auth 패키징 이슈로 RN 전용 export가 타입에서 안 잡힘(런타임엔 존재)
import { initializeAuth, getReactNativePersistence } from "firebase/auth";
import AsyncStorage from "@react-native-async-storage/async-storage";

const firebaseConfig = {
  apiKey: process.env.EXPO_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.EXPO_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.EXPO_PUBLIC_FIREBASE_APP_ID,
};

const app = initializeApp(firebaseConfig);

export const auth = initializeAuth(app, {
  persistence: getReactNativePersistence(AsyncStorage),
});
