# Allargando Frontend (Expo)

악기 취미생이 앙상블·버스킹 멤버를 무보수로 모집하는 서비스 **Allargando**(구 Obri, Poco a Poco)의 모바일 앱.
Expo Router 기반. 백엔드 실행 방법은 [`../../backend/README.md`](../../backend/README.md) 참고.

## 시작하기

```bash
npm install
npx expo start
```

실행 환경은 **development build**다. Firebase를 네이티브 SDK로 쓰므로 Expo Go에서는 앱이 열리지 않는다. 처음 한 번(그리고 네이티브 모듈·`app.json`의 네이티브 설정·`google-services.json`이 바뀔 때) 아래 명령으로 APK를 만들어 폰에 설치한다. 그 뒤에는 JS 코드만 바뀌므로 `npx expo start`에 연결해 바로 반영된다.

```bash
eas build --profile development --platform android
```

## 로컬 환경 설정 (필수)

백엔드 API 주소를 `.env`로 주입받는다. 이 파일은 `.gitignore` 대상 — 절대 커밋하지 않는다.

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

앱은 네이티브 Firebase SDK(`@react-native-firebase`)를 쓰므로 Expo Go가 아니라 **development build**에서 실행한다. 설정은 Firebase 콘솔 → 프로젝트 설정 → 내 앱(Android)에서 받은 `google-services.json`을 이 폴더에 두면 빌드에 들어간다(`.gitignore` 대상이라 커밋하지 않는다). 접근 권한이 없다면 프로젝트 관리자에게 콘솔 초대를 요청한다.

`.env`의 `EXPO_PUBLIC_FIREBASE_*` 값은 앱에서 쓰지 않고, 터미널에서 도는 개발용 스크립트(`scripts/register-test-user.mjs`)가 웹 SDK로 로그인할 때만 필요하다.

## 인증 현황

인증은 네이티브 Firebase SDK(`@react-native-firebase/auth`)로 **이메일/비밀번호** 로그인을 쓴다. 백엔드가 계정 고유성 앵커로 설계한 **전화번호 인증(SMS OTP)** 은 가입 화면에 붙이는 작업을 진행 중이다.

Firebase 인증 호출은 `lib/firebase.ts`(인스턴스), `contexts/AuthContext.tsx`(로그인 상태·가입), `lib/apiClient.ts`(ID 토큰 첨부) 세 곳에서만 한다. 화면 코드는 이 파일들을 경유하고 Firebase 모듈을 직접 import하지 않는다.

전환 시 함께 필요해지는 것들(지금은 불필요):
- `google-services.json` / `GoogleService-Info.plist` (Firebase 콘솔에서 다운로드, 이미 `.gitignore` 등록됨)
- `eas.json` 빌드 프로필 + `eas secret:create`로 시크릿 등록

## 파일 기반 라우팅

`app` 디렉터리 구조를 그대로 라우트로 사용한다. 자세한 내용은 [Expo Router 문서](https://docs.expo.dev/router/introduction) 참고.
