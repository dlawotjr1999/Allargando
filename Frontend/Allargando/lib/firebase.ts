// Firebase 인증 인스턴스를 앱 전체가 하나로 공유하도록 내보내는 모듈.
// 네이티브 Firebase SDK(@react-native-firebase)를 쓰므로, 웹 SDK 때처럼 apiKey·projectId 같은 설정값을 코드에서
// 넘기지 않는다. 설정은 빌드에 들어가는 google-services.json이 제공하고, 로그인 세션도 네이티브 쪽이 기기에
// 안전하게 보관하므로 AsyncStorage 영속화 코드도 필요 없다. 네이티브 모듈이라 Expo Go에서는 동작하지 않고
// development build(또는 스토어 빌드)에서만 쓸 수 있다.
import { getAuth } from "@react-native-firebase/auth";

export const auth = getAuth();
