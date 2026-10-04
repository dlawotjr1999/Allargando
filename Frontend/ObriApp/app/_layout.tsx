import { useEffect, useState } from "react";
import { Stack, Redirect } from "expo-router";
import { StatusBar } from "expo-status-bar";

import LoadingScreen from "@/components/common/LoadingScreen";
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
//  · 로그인했는데 서버에 가입 정보가 없음(가입을 못 끝낸 계정) → 가입 이어하기(계정 만들기는 건너뛴 프로필 단계)
//  · 로그인 + 프로필 있음 → 홈 탭
type Destination = "/(auth)/login" | "/(auth)/register/profile" | "/(tabs)/home";

function RootNavigator() {
  const { user, loading, profile, profileError, unregistered, registering } = useAuth();

  const target: Destination = !user ? "/(auth)/login" : unregistered ? "/(auth)/register/profile" : "/(tabs)/home";

  // 가입 제출 중에는 Firebase 계정이 생기는 순간 target이 바뀌어도 화면을 옮기지 않는다(AUTH-T11).
  // 가입 시작 전의 목적지를 기억해 두고 제출 중에는 그 값을 쓰며, 끝나면 곧바로 target을 쓴다.
  // 고정이 아니라 Redirect를 떼어 버리면 가입이 실패해 다시 붙을 때 현재 화면에서 로그인 화면으로 튄다
  const [heldTarget, setHeldTarget] = useState<Destination>(target);
  useEffect(() => {
    if (!registering) setHeldTarget(target);
  }, [registering, target]);
  const href = registering ? heldTarget : target;

  // 로그인은 돼 있는데 프로필 조회 결과가 아직 없는 동안(조회 중)에도 홈으로 먼저 보내지 않고 기다린다
  const profilePending = !!user && !profile && !profileError && !unregistered && !registering;

  if (loading || profilePending) {
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
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(tabs)" />
      </Stack>
      <Redirect href={href} />
      <StatusBar style="dark" />
    </>
  );
}
