import React from "react";
import LoadingScreen from "@/components/common/LoadingScreen";

// 앱을 켰을 때의 첫 경로("/"). 실제 목적지는 루트 레이아웃의 Redirect가 정하므로, 이동하기 전까지 로딩 화면을 이어서 보여 준다.
// 이 파일이 없으면 "/"가 +not-found로 해석돼 "페이지를 찾을 수 없어요" 화면이 잠깐 스친다
export default function IndexScreen() {
  return <LoadingScreen />;
}
