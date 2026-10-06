// 백엔드 API 호출 공통 레이어. 모든 응답이 { status, message, data } 포맷(CLAUDE.md 4장)이라
// 이 레이어에서 언랩하고, 실패 시 ApiError로 통일해 호출부가 매번 res.ok를 확인하지 않게 한다.
import { getIdToken } from "@react-native-firebase/auth";
import { auth } from "@/lib/firebase";

const BASE_URL = process.env.EXPO_PUBLIC_API_URL;

// 요청 하나의 제한 시간. 이 앱은 JSON 요청뿐이라 10초면 느린 망에서도 충분하고, 넘으면 재시도 UI로 넘긴다
const REQUEST_TIMEOUT_MS = 10_000;

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
  // 서버가 401을 주면 로그아웃 처리를 부르는지(기본 true). 가입 직후 요청처럼 세션을 잃으면 안 되는 호출만 false
  handleUnauthorized?: boolean;
}

// 서버가 401을 줬을 때 부를 동작(AuthProvider가 로그아웃으로 등록). Firebase는 만료된 토큰을 자동 갱신하므로
// 로그인 상태에서 서버가 401을 준다는 건 계정이 삭제·정지·폐기돼 더는 쓸 수 없다는 뜻이다
let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

// path 하나를 백엔드에 요청하고 data만 반환. 실패하면 ApiError를 throw
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, requiresAuth = true, handleUnauthorized = true } = options;

  const headers: Record<string, string> = { "Content-Type": "application/json" };

  if (requiresAuth) {
    // 로그인한 사용자의 ID 토큰을 가져온다. 만료가 가까우면 네이티브 SDK가 자동으로 갱신해 준다
    const currentUser = auth.currentUser;
    const idToken = currentUser ? await getIdToken(currentUser) : null;
    if (!idToken) {
      throw new ApiError(401, "로그인이 필요합니다");
    }
    headers.Authorization = `Bearer ${idToken}`;
  }

  // 응답이 오지 않는 요청이 화면을 영원히 붙잡지 않도록 시간 제한을 둔다(본문 읽기까지 포함)
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  let response: Response;
  let envelope: ApiEnvelope<T> | null;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });

    // 빈 본문·HTML(프록시/게이트웨이 오류 등)이면 JSON 파싱이 실패한다. 그때도 상태코드는 잃지 않도록 null로 받는다
    envelope = await response.json().catch(() => null);
  } catch (err) {
    if (controller.signal.aborted) {
      throw new ApiError(408, "서버 응답이 없어요. 잠시 후 다시 시도해주세요.");
    }
    // 연결 실패 같은 요청 단계의 오류는 화면에 일반 문구만 보여 원인을 알기 어려우므로, 개발 중에는 터미널에 남긴다
    if (__DEV__) console.warn(`[api] ${method} ${path} 요청 실패`, err);
    throw err;
  } finally {
    clearTimeout(timer);
  }

  if (!response.ok) {
    if (response.status === 401 && requiresAuth && handleUnauthorized) unauthorizedHandler?.();
    throw new ApiError(envelope?.status ?? response.status, envelope?.message ?? "요청을 처리할 수 없습니다");
  }

  // 성공(2xx)인데 본문이 JSON이 아니면 data를 만들 수 없으므로 오류로 통일한다
  if (!envelope) {
    throw new ApiError(response.status, "서버 응답을 해석할 수 없습니다");
  }

  return envelope.data;
}
