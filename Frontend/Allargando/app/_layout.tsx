import { useEffect, useState } from "react";
import { Stack, Redirect } from "expo-router";
import { StatusBar } from "expo-status-bar";

import LoadingScreen from "@/components/common/LoadingScreen";
import PushSetup from "@/components/push/PushSetup";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";
import { RegisterProvider } from "@/contexts/RegisterContext";

export default function RootLayout() {
  return (
    <AuthProvider>
      {/* 가입 폼 상태는 루트에 둔다 — 가입 실패 뒤 화면이 다시 만들어져도 입력한 값이 남아 있어야 한다 */}
      <RegisterProvider>
        <RootNavigator />
      </RegisterProvider>
    </AuthProvider>
  );
}

// 인증 상태에 따라 3갈래로 보낸다:
//  · 로그인 안 함 → 로그인 화면
//  · 로그인했는데 서버에 가입 정보가 없음(가입을 못 끝낸 계정) → 가입 이어하기. 전화 인증만 하고 이메일 연결 전에 끊긴
//    계정(이메일이 없음)은 이메일·비밀번호를 정하는 1단계부터, 이메일은 있는 계정은 프로필 단계부터 이어간다
//  · 로그인 + 프로필 있음 → 홈 탭
type Destination = "/(auth)/login" | "/(auth)/register" | "/(auth)/register/profile" | "/(tabs)/home";

function RootNavigator() {
  const { user, loading, profile, profilePending, unregistered, registering } = useAuth();

  const resumeDestination: Destination = user?.email ? "/(auth)/register/profile" : "/(auth)/register";
  const target: Destination = !user ? "/(auth)/login" : unregistered ? resumeDestination : "/(tabs)/home";

  // 가입 진행 중에는 전화 인증으로 로그인 상태가 바뀌어 target이 달라져도 화면을 옮기지 않는다.
  // 가입 시작 전의 목적지를 기억해 두고 진행 중에는 그 값을 쓰며, 끝나면 곧바로 target을 쓴다.
  // 고정이 아니라 Redirect를 떼어 버리면 가입이 실패해 다시 붙을 때 현재 화면에서 로그인 화면으로 튄다
  const [heldTarget, setHeldTarget] = useState<Destination>(target);
  useEffect(() => {
    if (!registering) setHeldTarget(target);
  }, [registering, target]);

  // 로딩(스플래시) 화면은 앱을 켠 직후 저장된 세션을 복원하고 프로필을 확인하는 동안에만 보인다.
  // 그 뒤 로그아웃→재로그인 때는 로그인 화면에 머물고(로그인 버튼이 진행 표시), 프로필이 오면 곧바로 이동한다
  const [booted, setBooted] = useState(false);
  useEffect(() => {
    if (!loading && !profilePending) setBooted(true);
  }, [loading, profilePending]);

  // 부팅 이후 프로필 조회 중에는 홈으로 먼저 보내지 않고(깜빡임·가입 화면 튕김 방지) 로그인 화면에 둔다
  const href = registering ? heldTarget : booted && profilePending ? "/(auth)/login" : target;

  if (loading || (profilePending && !booted)) {
    return (
      <>
        <LoadingScreen />
        <StatusBar style="dark" />
      </>
    );
  }

  return (
    <>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" />
      </Stack>
      <Redirect href={href} />
      {/* 서버에 가입된 로그인 사용자(프로필이 있음)가 있는 동안에만 푸시를 준비한다. 서버가 토큰을 유저 행에 저장하기 때문이다 */}
      {profile && <PushSetup />}
      <StatusBar style="dark" />
    </>
  );
}
