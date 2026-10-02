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
import CareerFormItem from "@/components/auth/CareerFormItem";

const INSTRUMENTS = ["피아노", "바이올린", "첼로", "플루트", "성악", "기타"];

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

  // 배열 index는 항목 삭제 시 뒤 요소가 앞으로 당겨져 재사용되므로,
  // React key로 쓰기 위한 항목별 안정적인 로컬 id를 별도로 관리한다.
  const keyCounter = useRef(0);
  const [careerKeys, setCareerKeys] = useState<number[]>(() =>
    careers.map(() => keyCounter.current++)
  );

  // 닉네임 중복 확인. 내 현재 닉네임은 서버 기준으로 "사용 중"이라 중복으로 나오므로 먼저 걸러낸다.
  const handleCheckNickname = async () => {
    const value = nickname.trim();
    if (!value) {
      Alert.alert("닉네임 확인", "닉네임을 입력해주세요.");
      return;
    }
    if (value === profile.nickname) {
      Alert.alert("닉네임 확인", "현재 사용 중인 닉네임입니다.");
      return;
    }
    try {
      const duplicated = await isNicknameDuplicated(value);
      Alert.alert("닉네임 확인", duplicated ? "이미 사용 중인 닉네임입니다." : "사용 가능한 닉네임입니다.");
    } catch {
      Alert.alert("닉네임 확인", "확인에 실패했어요. 잠시 후 다시 시도해주세요.");
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
    setCareers((prev) => [...prev, { organization: "", contexts: "" }]);
    setCareerKeys((prev) => [...prev, keyCounter.current++]);
  };

  // 프로필 저장 (PUT /api/users/me). 경력은 전체 교체라 항상 전체 목록을 보내며, 단체명이 빈 항목은 제외한다.
  // 응답이 곧 최신 프로필이라 재조회 없이 AuthContext에 바로 반영한다. 실패하면 이 화면에 남아 재시도할 수 있다.
  const handleSave = async () => {
    if (saving) return;
    setSaving(true);
    try {
      const updated = await updateMyInfo({
        nickname: nickname.trim(),
        instrument,
        careers: careers.filter((c) => c.organization.trim()),
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
            />
          </View>
          <TouchableOpacity style={styles.checkButton} onPress={handleCheckNickname}>
            <Text style={styles.checkButtonText}>중복 확인</Text>
          </TouchableOpacity>
        </View>

        <ChipSelect
          label="악기"
          options={INSTRUMENTS}
          selected={instrument}
          onSelect={setInstrument}
        />

        <Text style={styles.sectionLabel}>경력</Text>
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
          <Text style={styles.addButtonText}>경력 추가</Text>
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
