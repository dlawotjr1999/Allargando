// 로그인 상태(Firebase User)와 내 프로필(백엔드 /api/users/me) 전역 관리. onAuthStateChanged를 구독해
// 앱 어디서든 로그인 여부·ID Token 발급 경로(apiClient가 사용)를 단일 소스로 유지하고,
// 로그인되면 내 프로필도 한 번 불러와 마이페이지·모집글 상세(내 악기 강조) 등이 같은 값을 공유한다.
// 로그인했는데 서버에 가입 정보가 없으면(404) unregistered로 알려 가입 화면으로 보낼 수 있게 하고,
// 가입 제출(registerAccount)도 여기서 맡아 가입 도중 화면이 멋대로 옮겨 가지 않게 한다.
import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import {
  EmailAuthProvider,
  getIdToken,
  linkWithCredential,
  linkWithPhoneNumber,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPhoneNumber,
  signOut as firebaseSignOut,
  type User,
} from "@react-native-firebase/auth";
import { auth } from "@/lib/firebase";
import { getMyInfo } from "@/api/user";
import { registerUser, RegisterRequest } from "@/api/auth";
import { ApiError, setUnauthorizedHandler } from "@/lib/apiClient";
import { UserProfile } from "@/types/user";
import { toE164 } from "@/utils/registerValidation";

// 인증번호를 보낸 뒤 돌려받는 확인 핸들. 화면이 사용자가 입력한 코드를 이 핸들로 확인한다
export type PhoneConfirmation = Awaited<ReturnType<typeof signInWithPhoneNumber>>;

// 가입 제출 입력. email·password는 Firebase 계정을 새로 만들 때만 쓴다 —
// 가입 미완료로 이미 로그인된 상태에서 이어서 가입할 때는 필요 없다
export interface RegisterInput extends RegisterRequest {
  email?: string;
  password?: string;
}

interface AuthContextType {
  user: User | null;
  loading: boolean;
  // 백엔드 프로필. null이고 profileError·unregistered도 아니면 조회 중
  profile: UserProfile | null;
  // 프로필 조회 실패(네트워크 등). 가입 미완료(404)는 여기가 아니라 unregistered로 구분한다
  profileError: string | null;
  // 토큰은 유효한데 서버에 가입된 유저가 없음(404) — Firebase 계정만 만들고 가입을 못 끝낸 상태
  unregistered: boolean;
  // 가입 진행 중. 전화번호 인증 확인을 시작한 때부터 가입 제출이 끝나거나 가입 화면을 벗어날 때까지 true이고,
  // 이 동안은 로그인 상태가 바뀌어도 화면이 자동으로 옮겨 가지 않는다
  registering: boolean;
  // 로그인은 됐는데 프로필 조회 결과가 아직 없는 상태(조회 중). 이 동안은 어느 화면으로 보낼지 모른다
  profilePending: boolean;
  refreshProfile: () => Promise<void>;
  // 프로필 수정 응답처럼 이미 최신 값을 들고 있을 때 재조회 없이 바로 반영
  setProfile: (profile: UserProfile) => void;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  resetPassword: (email: string) => Promise<void>;
  registerAccount: (input: RegisterInput) => Promise<void>;
  // 전화번호(010-1234-5678 형식)로 인증번호 SMS를 보낸다. 반환된 핸들을 confirmPhoneCode에 넘겨 코드를 확인한다
  sendPhoneCode: (phoneNumber: string) => Promise<PhoneConfirmation>;
  // 사용자가 입력한 인증번호를 확인한다. 성공하면 그 번호로 인증된 상태가 되고, 실패하면 Firebase 오류를 그대로 던진다
  confirmPhoneCode: (confirmation: PhoneConfirmation, code: string) => Promise<void>;
  // 가입 화면을 벗어날 때 부른다. 전화 인증까지만 하고 끝내지 않은 임시 로그인을 정리한다
  abandonSignUp: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [unregistered, setUnregistered] = useState(false);
  const [registering, setRegistering] = useState(false);
  // state는 비동기로 반영돼 refreshProfile이 낡은 값을 볼 수 있어, 가입 중 여부는 ref로도 들고 있는다
  const registeringRef = useRef(false);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (firebaseUser) => {
      setUser(firebaseUser);
      setLoading(false);
    });
    return unsubscribe;
  }, []);

  // 서버가 401을 주면(계정 삭제·정지·토큰 폐기) 세션을 끊어 로그인 화면으로 돌려보낸다
  useEffect(() => {
    setUnauthorizedHandler(() => {
      firebaseSignOut(auth).catch(() => {});
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  // 내 프로필 조회. 404는 "Firebase엔 계정이 있는데 서버에 가입이 안 된" 상태(unregistered)로 구분하고,
  // 그 밖의 실패(네트워크 등)는 앱을 막지 않고 에러 메시지만 남겨 프로필이 필요한 화면이 재시도 UI를 보여줄 수 있게 한다.
  // 가입 제출 중에는 가입 흐름이 끝난 뒤 직접 부르므로 건너뛴다(가입 중 도착한 낡은 404가 상태를 덮지 않도록)
  const refreshProfile = useCallback(async () => {
    if (registeringRef.current) return;
    setProfileError(null);
    try {
      setProfile(await getMyInfo());
      setUnregistered(false);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setProfile(null);
        setUnregistered(true);
      } else {
        setProfileError(err instanceof ApiError ? err.message : "프로필을 불러오지 못했어요.");
      }
    }
  }, []);

  // 로그인되면 프로필을 불러오고, 로그아웃·탈퇴로 user가 사라지면 비운다(다음 계정에 이전 프로필이 비치지 않게)
  useEffect(() => {
    if (user) {
      refreshProfile();
    } else {
      setProfile(null);
      setProfileError(null);
      setUnregistered(false);
    }
  }, [user, refreshProfile]);

  const signIn = async (email: string, password: string) => {
    await signInWithEmailAndPassword(auth, email, password);
  };

  const signOut = async () => {
    // 가입 도중 다른 계정으로 바꾸는 경우에도 가입 진행 표시가 남아 화면 이동을 막지 않도록 함께 푼다
    registeringRef.current = false;
    setRegistering(false);
    await firebaseSignOut(auth);
  };

  // 비밀번호 재설정 메일 발송(Firebase 제공). 가입 여부와 무관하게 호출부는 같은 안내를 보여준다
  const resetPassword = async (email: string) => {
    await sendPasswordResetEmail(auth, email);
  };

  // 가입 제출: 전화번호 인증으로 로그인된 계정에 이메일·비밀번호를 연결한 뒤 서버에 가입한다(POST /api/auth/register).
  // 서버는 ID 토큰의 phone_number 값만 믿으므로 전화번호는 보내지 않는다 — 그래서 전화 인증을 마치지 않았으면 제출할 수 없다.
  // 이미 이메일이 연결된 계정(이메일 연결까지 끝내고 서버 가입만 실패해 이어서 가입하는 경우)은 연결을 건너뛴다.
  // 서버 가입이 실패해도 Firebase 계정은 지우지 않는다 — 서버가 멱등이라 같은 토큰으로 재시도하면 되고,
  // 지우면 동시 요청으로 정상 가입된 계정까지 사라질 수 있다. 이때 로그인은 유지돼 끝에서 프로필을 다시 조회하면
  // 가입 미완료(404)로 확정되어 가입 화면으로 이어진다. 실패는 그대로 던지므로 호출부가 안내를 띄운다
  const registerAccount = async (input: RegisterInput) => {
    registeringRef.current = true;
    setRegistering(true);
    try {
      const current = auth.currentUser;
      if (!current?.phoneNumber) {
        throw Object.assign(new Error("phone not verified"), { code: "auth/phone-not-verified" });
      }
      if (!current.email) {
        await linkWithCredential(current, EmailAuthProvider.credential(input.email ?? "", input.password ?? ""));
        // 연결 직전에 받아 둔 토큰에는 이메일이 없으므로, 서버가 이메일을 읽을 수 있게 토큰을 새로 받는다
        await getIdToken(current, true);
      }
      await registerUser({
        nickname: input.nickname,
        instrument: input.instrument,
        careers: input.careers,
      });
    } finally {
      registeringRef.current = false;
      setRegistering(false);
      // 성공이면 프로필이, 서버 가입이 실패했으면 가입 미완료(404)가 여기서 확정된다
      if (auth.currentUser) await refreshProfile();
    }
  };

  const profilePending = !!user && !profile && !profileError && !unregistered && !registering;

  // 전화번호 인증번호 발송. 이미 로그인된 계정이 있으면(이메일 계정만 만들고 전화 인증 전에 끊긴 경우) 그 계정에
  // 전화번호를 연결하고, 없으면 전화번호 계정으로 새로 로그인한다. 번호 형식 오류는 Firebase 오류와 같은 code로 던진다
  const sendPhoneCode = async (phoneNumber: string): Promise<PhoneConfirmation> => {
    const e164 = toE164(phoneNumber);
    if (!e164) {
      throw Object.assign(new Error("invalid phone number"), { code: "auth/invalid-phone-number" });
    }
    const current = auth.currentUser;
    return current ? linkWithPhoneNumber(current, e164) : signInWithPhoneNumber(auth, e164);
  };

  // 인증번호 확인. 성공하면 Firebase 로그인 상태가 바뀌어 화면 자동 이동이 일어나므로, 확인하기 전에 가입 진행 중으로
  // 표시해 막아 둔다. 실패(코드 오류·만료 등)하면 표시를 원래대로 되돌린다
  const confirmPhoneCode = async (confirmation: PhoneConfirmation, code: string) => {
    const wasRegistering = registeringRef.current;
    registeringRef.current = true;
    setRegistering(true);
    try {
      await confirmation.confirm(code);
    } catch (err) {
      registeringRef.current = wasRegistering;
      setRegistering(wasRegistering);
      throw err;
    }
  };

  // 가입 화면을 벗어날 때 정리한다. 가입이 끝났으면(진행 표시가 이미 풀림) 아무것도 하지 않고, 전화 인증만 하고 나간
  // 경우에는 진행 표시를 풀고 이메일이 아직 없는 임시 전화번호 계정을 로그아웃한다
  const abandonSignUp = async () => {
    if (!registeringRef.current) return;
    registeringRef.current = false;
    setRegistering(false);
    const current = auth.currentUser;
    if (current && !current.email) {
      await firebaseSignOut(auth).catch(() => {});
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        profile,
        profileError,
        unregistered,
        registering,
        profilePending,
        refreshProfile,
        setProfile,
        signIn,
        signOut,
        resetPassword,
        registerAccount,
        sendPhoneCode,
        confirmPhoneCode,
        abandonSignUp,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
