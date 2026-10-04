// 백엔드 API 호출 공통 레이어. 모든 응답이 { status, message, data } 포맷(CLAUDE.md 4장)이라
// 이 레이어에서 언랩하고, 실패 시 ApiError로 통일해 호출부가 매번 res.ok를 확인하지 않게 한다.
import { auth } from "@/lib/firebase";

const BASE_URL = process.env.EXPO_PUBLIC_API_URL;

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

interface ApiEnvelope<T> {
  status: number;
  message: string;
  data: T;
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  // 회원가입처럼 아직 로그인 상태가 아닌 요청만 false로 지정
  requiresAuth?: boolean;
}

// path 하나를 백엔드에 요청하고 data만 반환. 실패하면 ApiError를 throw
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, requiresAuth = true } = options;

  const headers: Record<string, string> = { "Content-Type": "application/json" };

  if (requiresAuth) {
    const idToken = await auth.currentUser?.getIdToken();
    if (!idToken) {
      throw new ApiError(401, "로그인이 필요합니다");
    }
    headers.Authorization = `Bearer ${idToken}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  // 빈 본문·HTML(프록시/게이트웨이 오류 등)이면 JSON 파싱이 실패한다. 그때도 상태코드는 잃지 않도록 null로 받는다
  const envelope: ApiEnvelope<T> | null = await response.json().catch(() => null);

  if (!response.ok) {
    throw new ApiError(envelope?.status ?? response.status, envelope?.message ?? "요청을 처리할 수 없습니다");
  }

  // 성공(2xx)인데 본문이 JSON이 아니면 data를 만들 수 없으므로 오류로 통일한다
  if (!envelope) {
    throw new ApiError(response.status, "서버 응답을 해석할 수 없습니다");
  }

  return envelope.data;
}
