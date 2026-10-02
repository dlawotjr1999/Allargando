export interface CareerEntry {
  organization: string;
  contexts: string;
}

export interface Career extends CareerEntry {
  id: number;
}

// 내 정보 조회(GET /api/users/me)·수정(PUT /api/users/me) 응답. 백엔드 UserResponseDTO와 1:1 대응.
// email은 선택 필드(CLAUDE.md §3.1)라 null일 수 있다.
export interface UserProfile {
  id: number;
  nickname: string;
  email: string | null;
  phoneNumber: string;
  instrument: string;
  careers: Career[];
  createdAt: string;
}
