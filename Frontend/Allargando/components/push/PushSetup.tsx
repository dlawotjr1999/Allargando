import { useEffect } from "react";
import { InteractionManager } from "react-native";
import { useRouter } from "expo-router";
import {
  getInitialNotification,
  getMessaging,
  onNotificationOpenedApp,
  onTokenRefresh,
} from "@react-native-firebase/messaging";
import { registerRefreshedToken, routeForNotification, syncPush } from "@/lib/push";

// 메시지 객체 타입은 라이브러리가 따로 내보내지 않으므로 리스너 인자 타입에서 꺼내 쓴다
type RemoteMessage = Parameters<Parameters<typeof onNotificationOpenedApp>[1]>[0];

const messaging = getMessaging();

// 서버에 가입된 로그인 사용자가 있는 동안 푸시를 준비하고 알림 이벤트를 처리하는 보이지 않는 컴포넌트.
// 루트 레이아웃이 프로필까지 불러온 뒤에만 이 컴포넌트를 렌더하므로(서버가 토큰을 유저 행에 저장하기 때문),
// 로그아웃하면 사라지며 모든 리스너도 함께 해제된다. 하는 일은 세 가지다.
//  1. 열릴 때 알림 권한을 확인·요청하고 이 기기를 서버에 등록한다(사용자가 알림을 꺼 둔 경우는 건너뜀)
//  2. FCM이 토큰을 새로 발급하면 서버에 새 값을 알린다
//  3. 알림 목록(휴대폰 상단을 내렸을 때)에서 알림을 눌러 앱이 열리면(백그라운드에서 돌아오거나 완전히 꺼진 상태에서
//     시작) 해당 화면으로 이동한다
// 알림 자체는 앱이 백그라운드이거나 꺼져 있을 때 시스템이 알림 목록에 띄워 준다. 앱이 화면에 떠 있는 동안 도착한
// 알림은 별도로 보여 주지 않는다(앱 안의 안내 창은 일부러 두지 않았다).
export default function PushSetup() {
  const router = useRouter();

  useEffect(() => {
    // 알림에 담긴 데이터로 열 화면을 정해 이동한다. 완전히 꺼진 상태에서 알림으로 시작하면 루트 레이아웃이 홈으로
    // 이동시키는 작업과 겹칠 수 있어, 그 화면 전환이 끝난 뒤에 이동한다
    const openFromNotification = (message: RemoteMessage) => {
      const route = routeForNotification(message.data);
      if (!route) return;
      InteractionManager.runAfterInteractions(() => router.push(route));
    };

    syncPush().catch((err) => {
      if (__DEV__) console.warn("[push] 기기 등록 실패", err);
    });

    const offTokenRefresh = onTokenRefresh(messaging, (token) => {
      registerRefreshedToken(token).catch((err) => {
        if (__DEV__) console.warn("[push] 갱신된 토큰 등록 실패", err);
      });
    });

    const offOpened = onNotificationOpenedApp(messaging, openFromNotification);
    getInitialNotification(messaging)
      .then((message) => {
        if (message) openFromNotification(message);
      })
      .catch(() => {});

    return () => {
      offTokenRefresh();
      offOpened();
    };
  }, [router]);

  return null;
}
