// 로그인 상태(Firebase User)와 내 프로필(백엔드 /api/users/me) 전역 관리. onAuthStateChanged를 구독해
// 앱 어디서든 로그인 여부·ID Token 발급 경로(apiClient가 사용)를 단일 소스로 유지하고,
// 로그인되면 내 프로필도 한 번 불러와 마이페이지·모집글 상세(내 악기 강조) 등이 같은 값을 공유한다.
// 로그인했는데 서버에 가입 정보가 없으면(404) unregistered로 알려 가입 화면으로 보낼 수 있게 하고,
// 가입 제출(registerAccount)도 여기서 맡아 가입 도중 화면이 멋대로 옮겨 가지 않게 한다.
import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import {
  EmailAuthProvider,
  deleteUser,
  getAdditionalUserInfo,
  getIdToken,
  linkWithCredential,
  linkWithPhoneNumber,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPhoneNumber,
  signOut as firebaseSignOut,
  updatePassword,
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
  // 계정 찾기용 인증번호 발송. 가입 때와 달리 항상 "전화번호로 로그인"하는 방식으로 보낸다
  sendRecoveryCode: (phoneNumber: string) => Promise<PhoneConfirmation>;
  // 계정 찾기용 인증번호 확인. 그 번호로 가입된 계정이 있어야 통과하고, 통과하면 그 계정의 이메일(없으면 null)을 돌려준다.
  // 이후 비밀번호를 바꾸거나 화면을 벗어날 때까지 로그인 상태를 유지하며, 끝나면 endRecovery로 반드시 로그아웃해야 한다
  confirmRecoveryCode: (confirmation: PhoneConfirmation, code: string) => Promise<{ email: string | null }>;
  // 전화 인증을 마친 계정의 비밀번호를 새로 정하고 로그아웃한다
  changePasswordAfterRecovery: (newPassword: string) => Promise<void>;
  // 계정 찾기를 끝낸다(화면을 벗어나거나 완료했을 때). 전화번호로 들어간 임시 로그인을 무조건 로그아웃한다
  endRecovery: () => Promise<void>;
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
  // 마지막으로 보낸 인증번호가 기존 로그인 계정에 전화번호를 연결하는 것이었는지(true) 새 로그인이었는지(false)
  const phoneLinkingRef = useRef(false);

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
    // 확인 단계에서 "연결"인지 "새 로그인"인지 알아야 하므로 보낼 때 기억해 둔다
    phoneLinkingRef.current = !!current;
    return current ? linkWithPhoneNumber(current, e164) : signInWithPhoneNumber(auth, e164);
  };

  // 지금 로그인된 계정이 서버에 가입돼 있는지 조회한다. 있으면 true, 가입 기록이 없으면(404) false.
  // 그 밖의 오류(네트워크 등)로는 판단할 수 없으므로 안전하게 로그아웃하고 연결 오류로 던진다
  const checkRegisteredOnServer = async (): Promise<boolean> => {
    try {
      await getMyInfo();
      return true;
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) return false;
      await firebaseSignOut(auth).catch(() => {});
      throw Object.assign(new Error("could not check registration"), { code: "auth/network-request-failed" });
    }
  };

  // 전화번호로 새로 로그인했는데 그 번호가 이미 가입된 계정의 것이면, 새로 가입하는 것이 아니라 기존 계정에 로그인된 것이다.
  // 비밀번호 없이 기존 계정에 들어가지 않도록 로그아웃하고 안내한다. 서버에 가입 기록이 없는 번호 계정(예전 가입 시도가
  // 중간에 끊긴 흔적)이면 그대로 이어서 가입한다
  const rejectIfAlreadyRegistered = async () => {
    if (!(await checkRegisteredOnServer())) return;
    await firebaseSignOut(auth).catch(() => {});
    throw Object.assign(new Error("phone already registered"), { code: "auth/phone-already-registered" });
  };

  // 인증번호 확인. 성공하면 Firebase 로그인 상태가 바뀌어 화면 자동 이동이 일어나므로, 확인하기 전에 가입 진행 중으로
  // 표시해 막아 둔다. 실패(코드 오류·만료 등)하면 표시를 원래대로 되돌린다
  const confirmPhoneCode = async (confirmation: PhoneConfirmation, code: string) => {
    const wasRegistering = registeringRef.current;
    registeringRef.current = true;
    setRegistering(true);
    try {
      const result = await confirmation.confirm(code);
      if (!phoneLinkingRef.current && result && getAdditionalUserInfo(result)?.isNewUser === false) {
        await rejectIfAlreadyRegistered();
      }
    } catch (err) {
      registeringRef.current = wasRegistering;
      setRegistering(wasRegistering);
      throw err;
    }
  };

  // 계정 찾기 인증번호 발송. 가입과 달리 로그인된 계정이 있어도 연결하지 않고 항상 전화번호로 로그인하는 방식이다
  const sendRecoveryCode = async (phoneNumber: string): Promise<PhoneConfirmation> => {
    const e164 = toE164(phoneNumber);
    if (!e164) {
      throw Object.assign(new Error("invalid phone number"), { code: "auth/invalid-phone-number" });
    }
    return signInWithPhoneNumber(auth, e164);
  };

  // 계정 찾기 인증번호 확인. 전화번호로 로그인하면 Firebase는 가입된 계정이 없을 때 번호만 있는 새 계정을 만들어 버리므로,
  // 인증 직후 그런 계정을 정리하고 "찾지 못함"으로 안내한다. 계정은 있어도 서버에 가입 기록이 없는 번호(가입을 마치지
  // 못한 흔적)는 찾을 계정이 아니므로 로그아웃하고 가입을 안내한다. 통과하면 로그인 상태를 유지한 채 이메일을 돌려주며,
  // 이 동안 화면이 자동으로 옮겨 가지 않도록 가입과 같은 "진행 중" 표시를 켜 둔다
  const confirmRecoveryCode = async (confirmation: PhoneConfirmation, code: string) => {
    const wasRegistering = registeringRef.current;
    registeringRef.current = true;
    setRegistering(true);
    try {
      const result = await confirmation.confirm(code);
      if (result && getAdditionalUserInfo(result)?.isNewUser === true && auth.currentUser) {
        await deleteUser(auth.currentUser).catch(() => firebaseSignOut(auth).catch(() => {}));
        throw Object.assign(new Error("account not found"), { code: "auth/account-not-found" });
      }
      if (!(await checkRegisteredOnServer())) {
        await firebaseSignOut(auth).catch(() => {});
        throw Object.assign(new Error("signup incomplete"), { code: "auth/signup-incomplete" });
      }
      return { email: auth.currentUser?.email ?? null };
    } catch (err) {
      registeringRef.current = wasRegistering;
      setRegistering(wasRegistering);
      throw err;
    }
  };

  // 전화 인증으로 들어온 계정의 비밀번호를 바꾸고 로그아웃한다. 방금 전화로 인증했으므로 재로그인 요구에 걸리지 않는다
  const changePasswordAfterRecovery = async (newPassword: string) => {
    const current = auth.currentUser;
    if (!current) {
      throw Object.assign(new Error("not signed in"), { code: "auth/user-not-found" });
    }
    await updatePassword(current, newPassword);
    await endRecovery();
  };

  // 계정 찾기를 끝낸다. 인증으로 들어온 임시 로그인이라 이메일 유무와 관계없이 무조건 로그아웃하고 진행 표시를 푼다.
  // 진행 중이 아니면(인증 전에 화면을 벗어난 경우) 아무것도 하지 않는다
  const endRecovery = async () => {
    if (!registeringRef.current) return;
    registeringRef.current = false;
    setRegistering(false);
    await firebaseSignOut(auth).catch(() => {});
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
        sendRecoveryCode,
        confirmRecoveryCode,
        changePasswordAfterRecovery,
        endRecovery,
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
