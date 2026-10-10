export interface CareerEntry {
  organization: string;
  contexts: string;
}

export interface Career extends CareerEntry {
  id: number;
}

// 다른 유저의 공개 프로필(GET /api/users/{nickname}) 응답. 백엔드 UserPublicProfileDTO와 1:1 대응 —
// 닉네임·악기·활동 이력만 있다. 전화번호·이메일 같은 연락처는 서버가 아예 내려주지 않고(전화번호는 지원 후
// 모집자에게만 공개되는 별도 정책), 가입일도 공개하지 않는다.
export interface UserPublicProfile {
  nickname: string;
  instrument: string;
  careers: Career[];
}

// 내 정보 조회(GET /api/users/me)·수정(PUT /api/users/me) 응답. 백엔드 UserResponseDTO와 1:1 대응.
// email은 선택 필드(CLAUDE.md §3.1)라 null일 수 있다.
export interface UserProfile {
  id: number;
  // 실명. 가입 때 받으며 모집자에게만 보인다(지원자 응답)
  name: string;
  nickname: string;
  email: string | null;
  phoneNumber: string;
  instrument: string;
  careers: Career[];
  createdAt: string;
}
