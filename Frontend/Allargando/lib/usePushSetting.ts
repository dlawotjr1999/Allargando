// 마이페이지의 "푸시 알림" 스위치 상태와 동작. 켜기에는 알림 권한 요청이 따르므로 결과(허용·거절·다시 묻지 않음)에 따라
// 사용자에게 다음에 할 일을 안내한다. 화면이 다시 보일 때마다 실제 상태(권한 + 사용자가 끈 적 없는지)를 새로 읽는다 —
// 사용자가 시스템 설정에서 권한을 바꾸고 돌아와도 스위치가 맞게 보이도록 하기 위해서다.
import { useCallback, useState } from "react";
import { Alert, Linking } from "react-native";
import { useFocusEffect } from "expo-router";
import { disablePush, enablePush, isPushActive } from "@/lib/push";

export function usePushSetting() {
  const [enabled, setEnabled] = useState(false);
  const [busy, setBusy] = useState(false);

  // 실제 상태를 읽어 스위치에 반영한다
  const refresh = useCallback(async () => {
    try {
      setEnabled(await isPushActive());
    } catch {
      // 상태를 읽지 못하면 이전 값을 그대로 둔다
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  // 스위치를 눌렀을 때. 켜는 경우 권한 요청 결과에 따라 안내하고, 켜지지 않았으면 스위치는 꺼진 채로 둔다.
  // 서버 호출이 실패해도 앱이 멈추지 않도록 오류는 안내 창으로 알리고 실제 상태를 다시 읽는다
  const toggle = async (next: boolean) => {
    if (busy) return;
    setBusy(true);
    try {
      if (next) {
        const result = await enablePush();
        if (result === "enabled") {
          setEnabled(true);
        } else if (result === "blocked") {
          Alert.alert("알림 권한이 꺼져 있어요", "휴대폰 설정에서 이 앱의 알림을 허용한 뒤 다시 켜 주세요.", [
            { text: "취소", style: "cancel" },
            { text: "설정 열기", onPress: () => Linking.openSettings() },
          ]);
        } else {
          Alert.alert("알림 권한이 필요해요", "알림을 받으려면 권한을 허용해 주세요.");
        }
      } else {
        await disablePush();
        setEnabled(false);
      }
    } catch (err) {
      if (__DEV__) console.warn("[push] 알림 설정 변경 실패", err);
      Alert.alert("알림 설정 실패", "잠시 후 다시 시도해 주세요.");
      await refresh();
    } finally {
      setBusy(false);
    }
  };

  return { enabled, busy, toggle };
}
