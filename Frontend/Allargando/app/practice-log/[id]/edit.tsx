import React, { useEffect, useState } from "react";
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
  ActivityIndicator,
} from "react-native";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import { useLocalSearchParams, useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import DateTimePicker from "@react-native-community/datetimepicker";
import { colors } from "@/constants/theme";
import { formatDate, parseDate, toDateOnly } from "@/utils/datetime";
import { getPracticeLog, updatePracticeLog } from "@/api/practiceLog";
import { ApiError } from "@/lib/apiClient";
import ThemedButton from "@/components/common/ThemedButton";

// 연습일지 수정 화면 — create.tsx와 같은 입력 폼을 쓰되, 진입 시 기존 값을 불러와 채워둔다.
export default function PracticeLogEditScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  const [title, setTitle] = useState("");
  const [date, setDate] = useState("");
  const [durationMinutes, setDurationMinutes] = useState("");
  const [content, setContent] = useState("");
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 진입 시 기존 값을 조회해 폼을 채운다. 목록에서 이미 받은 값을 재사용하지 않는 이유는
  // 상세 모달과 이 화면이 별도 진입점이라 항상 최신 값을 보장하려는 것(PracticeLogDetailModal과 동일한 이유).
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const log = await getPracticeLog(Number(id));
        if (cancelled) return;
        setTitle(log.title);
        setDate(log.logDate);
        setDurationMinutes(String(log.duration));
        setContent(log.content ?? "");
      } catch {
        if (cancelled) return;
        setLoadError(true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id]);

  const canSubmit = title.trim() !== "" && date !== "" && Number(durationMinutes) > 0;

  // 수정하기 버튼 핸들러 — PUT /api/practice-logs/{id} 호출.
  // 성공하면 router.back()으로 돌아가고, 목록 화면의 useFocusEffect가 재조회한다.
  const handleSubmit = async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      await updatePracticeLog(Number(id), {
        title: title.trim(),
        logDate: date,
        duration: Number(durationMinutes),
        content: content.trim() || undefined,
      });
      router.back();
    } catch (err) {
      Alert.alert(
        "수정 실패",
        err instanceof ApiError ? err.message : "연습일지를 수정하지 못했어요."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loading) {
    return (
      <SafeAreaView style={styles.container} edges={["top"]}>
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      </SafeAreaView>
    );
  }

  if (loadError) {
    return (
      <SafeAreaView style={styles.container} edges={["top"]}>
        <View style={styles.centerFill}>
          <Text style={styles.errorText}>연습일지를 불러오지 못했어요.</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => router.back()}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <Ionicons name="arrow-back" size={22} color={colors.primary} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>연습일지 수정</Text>
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
            <View style={[styles.fieldGroup, styles.rowField]}>
              <Text style={styles.label}>날짜</Text>
              <TouchableOpacity
                style={styles.input}
                onPress={() => setShowDatePicker(true)}
                activeOpacity={0.7}
              >
                <Text style={[styles.inputText, !date && styles.inputPlaceholder]}>
                  {date ? formatDate(date) : "날짜 선택"}
                </Text>
              </TouchableOpacity>
            </View>
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

          {showDatePicker && (
            <DateTimePicker
              value={date ? parseDate(date) : new Date()}
              mode="date"
              maximumDate={new Date()}
              onChange={(event, selected) => {
                setShowDatePicker(false);
                if (event.type === "set" && selected) {
                  setDate(toDateOnly(selected));
                }
              }}
            />
          )}

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
            title="수정하기"
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
  inputText: {
    fontSize: 14,
    color: colors.textPrimary,
  },
  inputPlaceholder: {
    color: colors.placeholder,
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
  centerFill: { flex: 1, alignItems: "center", justifyContent: "center" },
  errorText: {
    fontSize: 14,
    color: colors.textMuted,
  },
});
