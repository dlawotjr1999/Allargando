import { useEffect } from "react";
import { Stack } from "expo-router";
import { useAuth } from "@/contexts/AuthContext";
import { useRegisterForm } from "@/contexts/RegisterContext";

// 가입 3단계 화면의 스택. 가입 폼 상태(RegisterProvider)는 루트 레이아웃에 있다.
// 가입 화면을 벗어날 때(뒤로 가기로 로그인 화면에 돌아가거나 가입이 끝나 홈으로 갈 때) 전화 인증까지만 하고 끝내지 않은
// 임시 로그인을 정리한다. 정리하지 않으면 다음에 로그인 화면에서 다른 계정으로 들어갈 때 이전 인증 상태가 남는다
export default function RegisterLayout() {
  const { abandonSignUp } = useAuth();
  const { resetForm } = useRegisterForm();

  useEffect(() => {
    return () => {
      abandonSignUp();
      // 가입 화면을 벗어나면(포기하거나 끝났을 때) 입력한 아이디·비밀번호·이름이 메모리에 남지 않게 비운다.
      // 가입 제출이 실패해 화면에 머무는 동안은 이 정리가 돌지 않으므로 입력값이 유지된다
      resetForm();
    };
    // abandonSignUp은 매 렌더마다 새로 만들어지므로 의존성에 넣으면 화면을 벗어나기 전에 정리가 실행된다 — 언마운트 때만 부른다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return <Stack screenOptions={{ headerShown: false }} />;
}
