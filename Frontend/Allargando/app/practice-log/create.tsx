import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from "react-native";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { PRACTICE_CATEGORIES } from "@/constants/filterOptions";
import { createPracticeLog } from "@/api/practiceLog";
import { ApiError } from "@/lib/apiClient";
import ChipSelect from "@/components/common/ChipSelect";
import DateTimeField from "@/components/common/DateTimeField";
import ThemedButton from "@/components/common/ThemedButton";

export default function PracticeLogCreateScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [title, setTitle] = useState("");
  // 백엔드 PracticeLog.logDate가 LocalDate(날짜만)이므로 "YYYY-MM-DD"로 보관
  const [date, setDate] = useState("");
  const [durationMinutes, setDurationMinutes] = useState("");
  const [content, setContent] = useState("");
  const [category, setCategory] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 필수값: 제목·날짜·연습 시간. 내용은 선택
  const canSubmit = title.trim() !== "" && date !== "" && Number(durationMinutes) > 0;

  // 등록하기 버튼 핸들러 — POST /api/practice-logs 호출.
  // 성공하면 router.back()으로 목록 화면에 돌아가고, 그 화면의 useFocusEffect가
  // 포커스 시 재조회하므로 여기서 목록 state를 직접 갱신할 필요는 없다.
  // 내용(content)은 선택 입력이라 빈 문자열이면 undefined로 보내 백엔드 null과 구분한다.
  const handleSubmit = async () => {
    if (isSubmitting) return; // 버튼 연타로 중복 등록되는 것을 막는다
    setIsSubmitting(true);
    try {
      await createPracticeLog({
        title: title.trim(),
        logDate: date,
        duration: Number(durationMinutes),
        content: content.trim() || undefined,
        category: category || undefined,
      });
      router.back();
    } catch (err) {
      // ApiError면 백엔드가 내려준 메시지를, 그 외(네트워크 오류 등)는 기본 안내 문구를 보여준다
      Alert.alert(
        "등록 실패",
        err instanceof ApiError ? err.message : "연습일지를 등록하지 못했어요."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => router.back()}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <Ionicons name="arrow-back" size={22} color={colors.primary} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>연습일지 작성</Text>
        <View style={{ width: 22 }} />
      </View>

      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 80 }]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.fieldGroup}>
            <Text style={styles.label}>제목</Text>
            <TextInput
              style={styles.input}
              placeholder="연습한 곡이나 주제를 입력하세요"
              placeholderTextColor={colors.placeholder}
              value={title}
              onChangeText={setTitle}
            />
          </View>

          <View style={styles.row}>
            {/* 연습일지는 지난 기록을 남기는 곳이라 미래 날짜는 고를 수 없다 */}
            <DateTimeField
              mode="date"
              label="날짜"
              value={date}
              onChange={setDate}
              maximumDate={new Date()}
              style={styles.rowField}
            />
            <View style={[styles.fieldGroup, styles.rowField]}>
              <Text style={styles.label}>연습 시간(분)</Text>
              <TextInput
                style={styles.input}
                placeholder="90"
                placeholderTextColor={colors.placeholder}
                value={durationMinutes}
                onChangeText={(text) => setDurationMinutes(text.replace(/[^0-9]/g, ""))}
                keyboardType="number-pad"
              />
            </View>
          </View>

          {/* 선택 입력 — 고른 칩을 다시 누르면 선택이 풀린다 */}
          <ChipSelect
            label="분류 (선택)"
            options={PRACTICE_CATEGORIES}
            selected={category}
            onSelect={(value) => setCategory((prev) => (prev === value ? "" : value))}
          />

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>내용</Text>
            <TextInput
              style={[styles.input, styles.textArea]}
              placeholder="오늘 연습한 내용, 잘 된 점과 보완할 점을 자유롭게 기록해보세요"
              placeholderTextColor={colors.placeholder}
              value={content}
              onChangeText={setContent}
              multiline
              numberOfLines={10}
              textAlignVertical="top"
            />
          </View>
        </ScrollView>

        <View style={[styles.footer, { paddingBottom: insets.bottom + 12 }]}>
          <ThemedButton
            title="등록하기"
            disabled={!canSubmit}
            loading={isSubmitting}
            onPress={handleSubmit}
          />
        </View>
      </KeyboardAvoidingView>
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
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: colors.primary,
  },
  content: {
    padding: 24,
    gap: 24,
  },
  row: {
    flexDirection: "row",
    gap: 12,
  },
  rowField: {
    flex: 1,
  },
  fieldGroup: {
    gap: 8,
  },
  label: {
    fontSize: 12,
    color: colors.textMuted,
    letterSpacing: 1,
  },
  input: {
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 14,
    color: colors.textPrimary,
  },
  textArea: {
    height: 220,
    paddingTop: 12,
  },
  footer: {
    paddingHorizontal: 24,
    paddingTop: 12,
    backgroundColor: colors.background,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
});
