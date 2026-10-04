import { Stack } from "expo-router";

// 가입 3단계 화면의 스택. 가입 폼 상태(RegisterProvider)는 루트 레이아웃에 있다
export default function RegisterLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
