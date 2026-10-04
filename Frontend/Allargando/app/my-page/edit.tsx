import React, { useRef, useState } from "react";
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { isNicknameDuplicated, updateMyInfo } from "@/api/user";
import { ApiError } from "@/lib/apiClient";
import { useAuth } from "@/contexts/AuthContext";
import { CareerEntry, UserProfile } from "@/types/user";
import LoadingScreen from "@/components/common/LoadingScreen";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";
import ChipSelect from "@/components/common/ChipSelect";
import { INSTRUMENTS } from "@/constants/filterOptions";
import CareerFormItem from "@/components/auth/CareerFormItem";
import {
  CAREER_MAX_COUNT,
  getNicknameError,
  NICKNAME_HINT,
  normalizeNickname,
} from "@/utils/registerValidation";

// 프로필 수정 화면. 프로필은 마이페이지에서 이미 불러온 AuthContext 값이라 보통 곧바로 있지만,
// 없을 때(조회 전·실패)는 폼을 만들 수 없어 로딩 화면으로 둔다 — 폼은 초기값을 state로 복사하므로
// 프로필이 확정된 뒤에만 마운트해야 한다.
export default function EditProfileScreen() {
  const { profile } = useAuth();
  if (!profile) return <LoadingScreen />;
  return <EditProfileForm profile={profile} />;
}

function EditProfileForm({ profile }: { profile: UserProfile }) {
  const router = useRouter();
  const { setProfile } = useAuth();

  const [nickname, setNickname] = useState(profile.nickname);
  const [instrument, setInstrument] = useState(profile.instrument);
  const [careers, setCareers] = useState<CareerEntry[]>(
    profile.careers.map(({ organization, contexts }) => ({ organization, contexts }))
  );
  const [saving, setSaving] = useState(false);
  // 입력 중에도 형식이 틀리면 바로 알려준다. 이미 규칙에 안 맞는 기존 닉네임은 그대로 둘 수 있어
  // 바꾸지 않았다면 검사하지 않는다(서버도 새 입력만 검증한다)
  const nicknameError = normalizeNickname(nickname) === profile.nickname ? null : getNicknameError(nickname);

  // 배열 index는 항목 삭제 시 뒤 요소가 앞으로 당겨져 재사용되므로,
  // React key로 쓰기 위한 항목별 안정적인 로컬 id를 별도로 관리한다.
  const keyCounter = useRef(0);
  const [careerKeys, setCareerKeys] = useState<number[]>(() =>
    careers.map(() => keyCounter.current++)
  );

  // 닉네임 중복 확인. 내 현재 닉네임은 서버 기준으로 "사용 중"이라 중복으로 나오므로 먼저 걸러낸다.
  const handleCheckNickname = async () => {
    const value = normalizeNickname(nickname);
    if (!value) {
      Alert.alert("닉네임 확인", "닉네임을 입력해주세요.");
      return;
    }
    if (nicknameError) {
      Alert.alert("닉네임 확인", nicknameError);
      return;
    }
    if (value === profile.nickname) {
      Alert.alert("닉네임 확인", "현재 사용 중인 닉네임입니다.");
      return;
    }
    try {
      const duplicated = await isNicknameDuplicated(value);
      Alert.alert("닉네임 확인", duplicated ? "이미 사용 중인 닉네임입니다." : "사용 가능한 닉네임입니다.");
    } catch (err) {
      Alert.alert(
        "닉네임 확인",
        err instanceof ApiError && err.status === 400 ? err.message : "확인에 실패했어요. 잠시 후 다시 시도해주세요."
      );
    }
  };

  const handleCareerChange = (index: number, entry: CareerEntry) => {
    const updated = [...careers];
    updated[index] = entry;
    setCareers(updated);
  };

  const handleCareerRemove = (index: number) => {
    setCareers((prev) => prev.filter((_, i) => i !== index));
    setCareerKeys((prev) => prev.filter((_, i) => i !== index));
  };

  const handleCareerAdd = () => {
    if (careers.length >= CAREER_MAX_COUNT) {
      Alert.alert("활동 이력", `활동 이력은 최대 ${CAREER_MAX_COUNT}개까지 등록할 수 있어요.`);
      return;
    }
    setCareers((prev) => [...prev, { organization: "", contexts: "" }]);
    setCareerKeys((prev) => [...prev, keyCounter.current++]);
  };

  // 프로필 저장 (PUT /api/users/me). 활동 이력은 전체 교체라 항상 전체 목록을 보낸다. 단체명이 비어도 설명만 있는
  // 행은 의미가 있으므로 거르지 않고, 둘 다 빈 행만 서버가 버린다.
  // 응답이 곧 최신 프로필이라 재조회 없이 AuthContext에 바로 반영한다. 실패하면 이 화면에 남아 재시도할 수 있다.
  const handleSave = async () => {
    if (saving) return;
    if (!normalizeNickname(nickname)) {
      Alert.alert("입력을 확인해 주세요", "닉네임을 입력해 주세요.");
      return;
    }
    if (nicknameError) {
      Alert.alert("입력을 확인해 주세요", nicknameError);
      return;
    }
    setSaving(true);
    try {
      const updated = await updateMyInfo({
        nickname: normalizeNickname(nickname),
        instrument,
        careers: careers.map((c) => ({ organization: c.organization.trim(), contexts: c.contexts.trim() })),
      });
      setProfile(updated);
      router.back();
    } catch (err) {
      Alert.alert("저장 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => router.back()}
          activeOpacity={0.7}
          accessibilityLabel="뒤로 가기"
        >
          <Ionicons name="chevron-back" size={24} color={colors.textPrimary} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>프로필 수정</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.nicknameRow}>
          <View style={styles.nicknameInput}>
            <ThemedInput
              label="닉네임"
              icon="person-outline"
              placeholder="닉네임 입력"
              value={nickname}
              onChangeText={setNickname}
              autoCapitalize="none"
              autoCorrect={false}
              maxLength={20}
            />
          </View>
          <TouchableOpacity style={styles.checkButton} onPress={handleCheckNickname}>
            <Text style={styles.checkButtonText}>중복 확인</Text>
          </TouchableOpacity>
        </View>
        <Text style={[styles.nicknameHint, nicknameError ? styles.hintError : null]}>{NICKNAME_HINT}</Text>

        <ChipSelect
          label="악기"
          options={INSTRUMENTS}
          selected={instrument}
          onSelect={setInstrument}
        />

        <Text style={styles.sectionLabel}>활동 이력</Text>
        {careers.map((career, index) => (
          <CareerFormItem
            key={careerKeys[index]}
            value={career}
            index={index}
            onChange={handleCareerChange}
            onRemove={handleCareerRemove}
            removable
          />
        ))}
        <TouchableOpacity style={styles.addButton} onPress={handleCareerAdd}>
          <Ionicons name="add" size={18} color={colors.textMuted} />
          <Text style={styles.addButtonText}>활동 이력 추가</Text>
        </TouchableOpacity>

        <View style={styles.bottom}>
          <ThemedButton title="저장" onPress={handleSave} loading={saving} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: colors.textPrimary,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 20,
  },
  nicknameRow: {
    flexDirection: "row",
    alignItems: "flex-end",
    gap: 8,
  },
  nicknameInput: {
    flex: 1,
  },
  checkButton: {
    backgroundColor: colors.surface,
    borderWidth: 0.5,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 14,
    height: 46,
    justifyContent: "center",
    marginBottom: 16,
  },
  checkButtonText: {
    fontSize: 13,
    color: colors.textSecondary,
  },
  // 입력줄(아래 여백 16)에 붙여 안내문을 입력칸 바로 밑에 둔다
  nicknameHint: {
    fontSize: 11,
    color: colors.textMuted,
    lineHeight: 16,
    marginTop: -10,
    marginBottom: 16,
  },
  hintError: {
    color: colors.danger,
  },
  sectionLabel: {
    fontSize: 12,
    color: colors.textSecondary,
    letterSpacing: 1,
    marginBottom: 10,
  },
  addButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    borderWidth: 1,
    borderStyle: "dashed",
    borderColor: colors.border,
    borderRadius: 10,
    height: 44,
    marginBottom: 24,
  },
  addButtonText: {
    fontSize: 13,
    color: colors.textMuted,
  },
  bottom: {
    marginBottom: 32,
  },
});
