// 지원 시 보여 주는 개인정보 제3자 제공 동의 문안의 버전. 서버가 같은 값(Application.CONSENT_VERSION)과 대조해
// 다르면 지원을 400으로 거절한다. 동의 화면의 문안(components/application/ApplicationSubmitModal.tsx,
// docs/T&C/apply-consent-screen.md)을 바꿀 때만 올리고, 서버 값과 같은 배포에서 함께 바꾼다.
export const APPLY_CONSENT_VERSION = "2026-10-15";
