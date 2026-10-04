// 로그인 상태(Firebase User)와 내 프로필(백엔드 /api/users/me) 전역 관리. onAuthStateChanged를 구독해
// 앱 어디서든 로그인 여부·ID Token 발급 경로(apiClient가 사용)를 단일 소스로 유지하고,
// 로그인되면 내 프로필도 한 번 불러와 마이페이지·모집글 상세(내 악기 강조) 등이 같은 값을 공유한다.
// 로그인했는데 서버에 가입 정보가 없으면(404) unregistered로 알려 가입 화면으로 보낼 수 있게 하고,
// 가입 제출(registerAccount)도 여기서 맡아 가입 도중 화면이 멋대로 옮겨 가지 않게 한다.
import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  User,
} from "firebase/auth";
import { auth } from "@/lib/firebase";
import { getMyInfo } from "@/api/user";
import { registerUser, RegisterRequest } from "@/api/auth";
import { ApiError, setUnauthorizedHandler } from "@/lib/apiClient";
import { UserProfile } from "@/types/user";

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
  // 가입 제출 진행 중. 이 동안 화면 자동 이동을 막는다
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
    await firebaseSignOut(auth);
  };

  // 비밀번호 재설정 메일 발송(Firebase 제공). 가입 여부와 무관하게 호출부는 같은 안내를 보여준다
  const resetPassword = async (email: string) => {
    await sendPasswordResetEmail(auth, email);
  };

  // 가입 제출: Firebase 계정 생성 → 서버 가입(POST /api/auth/register).
  // 서버 가입이 실패해도 Firebase 계정은 지우지 않는다 — 서버가 멱등이라 같은 토큰으로 재시도하면 되고,
  // 지우면 동시 요청으로 정상 가입된 계정까지 사라질 수 있다(CLAUDE.md §3.1). 이때 로그인은 유지돼
  // 끝에서 프로필을 다시 조회하면 404(unregistered)로 확정되어 가입 화면으로 이어진다.
  // 실패는 그대로 던지므로 호출부가 안내를 띄운다
  const registerAccount = async (input: RegisterInput) => {
    registeringRef.current = true;
    setRegistering(true);
    try {
      if (!auth.currentUser) {
        await createUserWithEmailAndPassword(auth, input.email ?? "", input.password ?? "");
      }
      await registerUser({
        nickname: input.nickname,
        phoneNumber: input.phoneNumber,
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
