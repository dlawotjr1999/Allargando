// 신고·차단 화면 동작 공통 로직 — 모집글 상세와 지원자 목록 두 화면이 같은 확인·알림 흐름을 쓴다.
import { Alert } from "react-native";
import { blockUser } from "@/api/block";
import { ApiError } from "@/lib/apiClient";
import { markPostListStale } from "@/lib/postListRefresh";

// 차단 확인 → 차단 요청. 차단하면 그 유저의 모집글이 목록에서 빠지므로 목록 갱신 표시를 남긴다.
// onBlocked는 성공 후 화면별 후속 동작(예: 상세 화면에서 뒤로 가기)
export function confirmBlockUser(nickname: string, onBlocked?: () => void) {
  Alert.alert(
    "사용자 차단",
    `${nickname} 님을 차단할까요?\n이 사용자의 모집글이 목록에서 사라지고, 내 모집글에 지원할 수 없게 돼요. 설정 > 차단 관리에서 언제든 해제할 수 있어요.`,
    [
      { text: "취소", style: "cancel" },
      {
        text: "차단",
        style: "destructive",
        onPress: async () => {
          try {
            await blockUser(nickname);
            markPostListStale();
            Alert.alert("차단했어요", `${nickname} 님을 차단했어요.`);
            onBlocked?.();
          } catch (err) {
            Alert.alert("차단 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
          }
        },
      },
    ]
  );
}
