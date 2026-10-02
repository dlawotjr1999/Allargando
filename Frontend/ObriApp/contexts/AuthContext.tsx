// 로그인 상태(Firebase User)와 내 프로필(백엔드 /api/users/me) 전역 관리. onAuthStateChanged를 구독해
// 앱 어디서든 로그인 여부·ID Token 발급 경로(apiClient가 사용)를 단일 소스로 유지하고,
// 로그인되면 내 프로필도 한 번 불러와 마이페이지·모집글 상세(내 악기 강조) 등이 같은 값을 공유한다.
import React, { createContext, useCallback, useContext, useEffect, useState } from "react";
import {
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  User,
} from "firebase/auth";
import { auth } from "@/lib/firebase";
import { getMyInfo } from "@/api/user";
import { ApiError } from "@/lib/apiClient";
import { UserProfile } from "@/types/user";

interface AuthContextType {
  user: User | null;
  loading: boolean;
  // 백엔드 프로필. null이고 profileError도 null이면 조회 중, profileError가 있으면 조회 실패
  profile: UserProfile | null;
  profileError: string | null;
  refreshProfile: () => Promise<void>;
  // 프로필 수정 응답처럼 이미 최신 값을 들고 있을 때 재조회 없이 바로 반영
  setProfile: (profile: UserProfile) => void;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  resetPassword: (email: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (firebaseUser) => {
      setUser(firebaseUser);
      setLoading(false);
    });
    return unsubscribe;
  }, []);

  // 내 프로필 조회. 실패(예: Firebase엔 계정이 있는데 백엔드 가입이 안 된 경우)해도 앱을 막지 않고
  // 에러 메시지만 남겨, 프로필이 필요한 화면이 재시도 UI를 보여줄 수 있게 한다.
  const refreshProfile = useCallback(async () => {
    setProfileError(null);
    try {
      setProfile(await getMyInfo());
    } catch (err) {
      setProfileError(err instanceof ApiError ? err.message : "프로필을 불러오지 못했어요.");
    }
  }, []);

  // 로그인되면 프로필을 불러오고, 로그아웃·탈퇴로 user가 사라지면 비운다(다음 계정에 이전 프로필이 비치지 않게)
  useEffect(() => {
    if (user) {
      refreshProfile();
    } else {
      setProfile(null);
      setProfileError(null);
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

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        profile,
        profileError,
        refreshProfile,
        setProfile,
        signIn,
        signOut,
        resetPassword,
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
