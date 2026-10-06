// 푸시 알림(FCM) 연동. 알림에는 두 갈래가 있다.
//  · 내 계정으로 오는 알림(지원 결과·새 지원 도착·내가 지원한 글의 수정·삭제): 이 기기의 FCM 토큰을 서버에 등록해 두면
//    서버가 그 토큰으로 보낸다.
//  · 모두에게 가는 새 모집글 알림: 서버가 "new_post" 토픽으로 한꺼번에 보내므로, 이 기기가 그 토픽을 구독해야 받는다.
// "알림 켜기/끄기"는 이 두 가지를 함께 켜고 끈다. 끈 상태는 FCM 자동 초기화(autoInit) 설정으로 기기에 저장한다 —
// 별도 저장소 없이 앱을 다시 켜도 유지되고, 꺼 두면 FCM이 토큰을 만들거나 갱신하지 않는다.
import { PermissionsAndroid, Platform } from "react-native";
import {
  deleteToken,
  getMessaging,
  getToken,
  isAutoInitEnabled,
  setAutoInitEnabled,
  setBackgroundMessageHandler,
  subscribeToTopic,
  unsubscribeFromTopic,
} from "@react-native-firebase/messaging";
import { clearFcmToken, registerFcmToken } from "@/api/auth";

// 서버(NotificationService)가 새 모집글 알림을 보내는 토픽 이름. 서버와 반드시 같아야 한다
export const NEW_POST_TOPIC = "new_post";

const messaging = getMessaging();

// 앱이 완전히 꺼져 있거나 백그라운드일 때 도착한 메시지를 처리하는 자리. 서버가 알림 내용(notification)을 함께 보내서
// 시스템이 알림을 직접 띄워 주므로 따로 할 일은 없다. 다만 등록하지 않으면 백그라운드 수신 시 경고가 나므로 빈 처리기를 둔다.
// 이 파일이 앱 시작 때 로드되므로(루트 레이아웃이 가져온다) 백그라운드 수신 시에도 이 줄이 실행된다
setBackgroundMessageHandler(messaging, async () => {});

export type EnableResult = "enabled" | "denied" | "blocked";

// 알림 권한이 이미 허용돼 있는지. Android 13 이상에서만 런타임 권한이 있고, 그보다 낮은 버전은 항상 허용으로 본다
export async function hasNotificationPermission(): Promise<boolean> {
  if (Platform.OS !== "android" || Platform.Version < 33) return true;
  return PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
}

// 알림 권한을 요청한다. 시스템 창은 사용자가 두 번 거절하면 더 뜨지 않고 "다시 묻지 않음"(blocked)이 되며,
// 그때는 설정 화면에서 직접 허용해야 한다
async function requestNotificationPermission(): Promise<"granted" | "denied" | "blocked"> {
  if (Platform.OS !== "android" || Platform.Version < 33) return "granted";
  const result = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
  if (result === PermissionsAndroid.RESULTS.GRANTED) return "granted";
  return result === PermissionsAndroid.RESULTS.NEVER_ASK_AGAIN ? "blocked" : "denied";
}

// 알림이 실제로 켜져 있는지: 사용자가 끄지 않았고(autoInit) 권한도 허용돼 있어야 한다. 설정 화면의 스위치 상태에 쓴다
export async function isPushActive(): Promise<boolean> {
  return isAutoInitEnabled(messaging) && (await hasNotificationPermission());
}

// 이 기기의 FCM 토큰을 서버에 등록하고 새 모집글 토픽을 구독한다. 로그인된 계정이 서버에 가입돼 있어야 한다
// (서버가 토큰을 그 유저 행에 저장한다). 개발 중에는 Firebase 콘솔의 "테스트 메시지"에 붙여 넣어 시험할 수 있도록 토큰을 출력한다
export async function registerDevice(): Promise<void> {
  const token = await getToken(messaging);
  if (__DEV__) console.log("[push] FCM 토큰", token);
  await registerFcmToken(token);
  await subscribeToTopic(messaging, NEW_POST_TOPIC);
}

// 토큰이 새로 발급돼(앱 재설치·데이터 삭제·FCM의 주기적 갱신) 서버에 있는 값이 낡았을 때 새 값을 알린다
export async function registerRefreshedToken(token: string): Promise<void> {
  await registerFcmToken(token);
}

// 알림 켜기. 권한이 없으면 먼저 요청하고, 허용되면 자동 초기화를 켠 뒤 토큰 등록과 토픽 구독까지 한다.
// 거절하면 "denied", 더는 시스템 창을 띄울 수 없으면 "blocked"를 돌려줘 화면이 설정으로 안내하게 한다
export async function enablePush(): Promise<EnableResult> {
  if (!(await hasNotificationPermission())) {
    const permission = await requestNotificationPermission();
    if (permission !== "granted") return permission;
  }
  await setAutoInitEnabled(messaging, true);
  await registerDevice();
  return "enabled";
}

// 알림 끄기. 서버의 토큰 연결과 토픽 구독을 풀고, 자동 초기화를 꺼 이 선택을 기기에 저장하며, 이 기기의 토큰을 폐기해
// 서버 호출이 실패하더라도 낡은 토큰으로는 알림이 오지 않게 한다. 각 단계는 따로 실패를 삼켜 하나가 막혀도 나머지를 진행한다
export async function disablePush(): Promise<void> {
  await clearFcmToken().catch(() => {});
  await unsubscribeFromTopic(messaging, NEW_POST_TOPIC).catch(() => {});
  await setAutoInitEnabled(messaging, false).catch(() => {});
  await deleteToken(messaging).catch(() => {});
}

// 로그인 직후(또는 앱을 켤 때)의 동기화. 사용자가 끄지 않았다면 권한을 확인·요청하고 토큰과 토픽을 맞춘다.
// 권한을 이미 거절했거나 막아 둔 사용자에게는 조용히 넘어간다(설정 화면의 스위치로 다시 켤 수 있다).
// 시스템 창은 거절을 두 번 하면 더 뜨지 않으므로 앱을 켤 때마다 물어도 사용자가 계속 시달리지 않는다
export async function syncPush(): Promise<void> {
  if (!isAutoInitEnabled(messaging)) return;
  if (!(await hasNotificationPermission())) {
    if ((await requestNotificationPermission()) !== "granted") return;
  }
  await registerDevice();
}

// 로그아웃·탈퇴 직전에 이 기기를 내 계정에서 떼어 낸다. 토큰은 기기에 속해서, 풀지 않으면 같은 기기에서 다른 계정으로
// 로그인했을 때 이전 계정의 알림이 도착한다. 토픽도 풀어 로그아웃한 기기에 새 모집글 알림이 가지 않게 한다.
// 실패해도 로그아웃·탈퇴를 막지 않는다(서버는 다음 로그인 때 토큰의 주인을 바로잡는다)
export async function releasePush(): Promise<void> {
  await clearFcmToken().catch(() => {});
  await unsubscribeFromTopic(messaging, NEW_POST_TOPIC).catch(() => {});
}

// 알림에 담긴 데이터(type·postId 등)를 해석해 눌렀을 때 열 화면을 정한다. 알 수 없는 알림은 null(앱만 열고 이동하지 않음).
//  · 새 모집글·글 수정: 그 글의 상세
//  · 새 지원 도착(모집자): 그 글의 지원자 목록
//  · 지원 결과·글 삭제: 내 지원 내역이 있는 마이페이지(삭제된 글은 열 수 없으므로)
export type NotificationRoute =
  | { pathname: "/post/[id]"; params: { id: string } }
  | { pathname: "/post/[id]/applicants"; params: { id: string } }
  | { pathname: "/(tabs)/my-page" };

export function routeForNotification(data: Record<string, unknown> | undefined): NotificationRoute | null {
  if (!data) return null;
  const type = typeof data.type === "string" ? data.type : "";
  // 글 번호는 서버가 숫자 문자열로 보낸다. 형식이 이상한 값으로 엉뚱한 경로가 만들어지지 않게 숫자만 받는다
  const postId = typeof data.postId === "string" && /^\d+$/.test(data.postId) ? data.postId : null;

  switch (type) {
    case "NEW_POST":
    case "POST_UPDATED":
      return postId ? { pathname: "/post/[id]", params: { id: postId } } : null;
    case "NEW_APPLICATION":
      return postId ? { pathname: "/post/[id]/applicants", params: { id: postId } } : null;
    case "APPLICATION_RESULT":
    case "POST_DELETED":
      return { pathname: "/(tabs)/my-page" };
    default:
      return null;
  }
}
