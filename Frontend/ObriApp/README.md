# Poco a Poco Frontend (Expo)

악기 취미생이 앙상블·버스킹 멤버를 무보수로 모집하는 서비스 **Poco a Poco**(구 Obri)의 모바일 앱.
Expo Router 기반. 백엔드 실행 방법은 [`../../backend/README.md`](../../backend/README.md) 참고.

## 시작하기

```bash
npm install
npx expo start
```

실행 환경은 **Expo Go**다. Firebase는 네이티브 모듈이 아닌 `firebase` JS SDK를 사용하므로 별도 개발 빌드 없이 Expo Go에서 그대로 동작한다.

## 로컬 환경 설정 (필수)

백엔드 API 주소와 Firebase 웹 설정을 `.env`로 주입받는다. 이 파일은 `.gitignore` 대상 — 절대 커밋하지 않는다.

```bash
cp .env.example .env
```

### API 주소

**실기기 Expo Go에서는 `localhost`가 폰 자기 자신을 가리켜 백엔드에 붙지 않는다.** 개발 머신의 LAN IP를 써야 한다.

| 실행 환경 | `EXPO_PUBLIC_API_URL` |
|---|---|
| 실기기 Expo Go | `http://<개발머신 LAN IP>:8080` |
| iOS 시뮬레이터 | `http://localhost:8080` |
| Android 에뮬레이터 | `http://10.0.2.2:8080` |

폰과 개발 머신이 같은 Wi-Fi에 있어야 하고, 방화벽에서 8080 인바운드가 열려 있어야 한다.

### Firebase 설정

Firebase 콘솔 → 프로젝트 설정 → 내 앱 → 웹 앱의 `firebaseConfig` 값을 `.env`의 `EXPO_PUBLIC_FIREBASE_*` 항목에 채운다. 접근 권한이 없다면 프로젝트 관리자에게 콘솔 초대를 요청한다.

이 값들은 클라이언트에 노출되는 것이 정상이다(공개 식별자). 실제 접근 제어는 Firebase 보안 규칙과 백엔드의 ID Token 검증이 담당한다.

## 인증 현황

현재 인증은 **Firebase 이메일/비밀번호**를 사용한다. 백엔드가 계정 고유성 앵커로 설계한 **전화번호 인증(SMS OTP)은 출시 전 하드닝 단계로 미뤄져 있다** — Phone Auth가 Expo Go에서 동작하지 않아 EAS development build가 선행돼야 하기 때문이다.

그 전환 시점에 `@react-native-firebase/auth`로 SDK를 교체할 가능성이 높다. 따라서 **화면 코드에서 `firebase/auth`를 직접 import하지 않는다** — 인증 호출은 전부 `api/auth.ts` 래퍼를 경유시켜 교체 지점을 한 파일로 고정한다.

전환 시 함께 필요해지는 것들(지금은 불필요):
- `google-services.json` / `GoogleService-Info.plist` (Firebase 콘솔에서 다운로드, 이미 `.gitignore` 등록됨)
- `eas.json` 빌드 프로필 + `eas secret:create`로 시크릿 등록

## 파일 기반 라우팅

`app` 디렉터리 구조를 그대로 라우트로 사용한다. 자세한 내용은 [Expo Router 문서](https://docs.expo.dev/router/introduction) 참고.
