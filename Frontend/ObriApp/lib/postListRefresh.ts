// 모집글 목록(탭)이 다음에 화면에 돌아왔을 때 다시 조회해야 하는지를 화면 간에 전달하는 플래그.
// 모집글을 등록·수정·삭제하거나 유저를 차단·해제하면 목록 내용이 달라지는데, 목록 탭은 화면이 유지된 채
// 포커스만 돌아오므로 스스로는 알 수 없다 — 변경을 일으킨 화면이 표시하고, 목록 화면이 포커스 때 소비한다.
let stale = false;

// 목록이 낡았음을 표시
export function markPostListStale() {
  stale = true;
}

// 낡았다면 true를 돌려주고 표시를 지운다(한 번 소비하면 다시 표시되기 전까지 false)
export function consumePostListStale(): boolean {
  const wasStale = stale;
  stale = false;
  return wasStale;
}
