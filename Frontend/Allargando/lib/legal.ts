// 이용약관·개인정보처리방침 문서 열기. 문서는 공개 URL로 호스팅하며(Play 심사 조건) 주소는 빌드 환경변수로 받는다.
// 값이 비어 있으면(아직 호스팅 전) "준비 중" 안내만 띄우고, 채워지는 순간 코드 수정 없이 링크가 열린다.
import { Alert, Linking } from "react-native";

export const TERMS_URL = process.env.EXPO_PUBLIC_TERMS_URL;
export const PRIVACY_URL = process.env.EXPO_PUBLIC_PRIVACY_URL;

// url의 문서를 외부 브라우저로 연다. title은 실패·준비 중 안내에 쓴다
export async function openLegalDocument(title: string, url: string | undefined) {
  if (!url) {
    Alert.alert("준비 중입니다", `${title}은(는) 곧 공개될 예정이에요.`);
    return;
  }
  try {
    await Linking.openURL(url);
  } catch {
    Alert.alert("링크를 열 수 없어요", "잠시 후 다시 시도해 주세요.");
  }
}
