import React, { useRef, useState } from "react";
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
import { CATEGORIES, REGIONS } from "@/constants/filterOptions";
import { ApiError } from "@/lib/apiClient";
import { PostCreateRequest } from "@/types/post";
import ThemedButton from "@/components/common/ThemedButton";
import ChipSelect from "@/components/common/ChipSelect";
import PostInstrumentFormItem, {
  PostInstrumentDraft,
} from "@/components/post/PostInstrumentFormItem";

interface PostFormProps {
  mode: "create" | "edit";
  // 수정 모드에서 채워 넣을 기존 값. eventAt은 서버 ISO 형식("2026-08-01T14:00:00")을 날짜·시간 입력칸으로 쪼갠다
  initial?: PostCreateRequest;
  // 폼 값을 서버에 보내는 동작(등록/수정 API 호출 + 성공 후 화면 이동). 실패하면 throw — 이 폼이 알림을 띄운다
  onSubmit: (payload: PostCreateRequest) => Promise<void>;
}

// 모집글 등록·수정 공용 폼 (헤더 + 입력 + 하단 버튼). 등록·수정이 같은 요청 바디(PostCreateRequest)를 쓰므로
// 화면은 mode와 initial·onSubmit만 다르게 넘기면 된다.
export default function PostForm({ mode, initial, onSubmit }: PostFormProps) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const isEdit = mode === "edit";

  const [category, setCategory] = useState(initial?.category ?? "");
  const [title, setTitle] = useState(initial?.title ?? "");
  const [eventDate, setEventDate] = useState(initial?.eventAt.slice(0, 10) ?? "");
  const [eventTime, setEventTime] = useState(initial?.eventAt.slice(11, 16) ?? "");
  const [location, setLocation] = useState(initial?.location ?? "");
  const [region, setRegion] = useState(initial?.region ?? "");
  const [timetable, setTimetable] = useState(initial?.timetable ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [submitting, setSubmitting] = useState(false);

  const [instruments, setInstruments] = useState<PostInstrumentDraft[]>(() =>
    initial
      ? initial.instruments.map(({ instrument, people }) => ({ instrument, people: String(people) }))
      : [{ instrument: "", people: "" }]
  );
  // 배열 index는 항목 삭제 시 뒤 요소가 앞으로 당겨져 재사용되므로,
  // React key로 쓰기 위한 항목별 안정적인 로컬 id를 별도로 관리한다.
  const keyCounter = useRef(0);
  const [instrumentKeys, setInstrumentKeys] = useState<number[]>(() =>
    instruments.map(() => keyCounter.current++)
  );

  const handleInstrumentChange = (index: number, entry: PostInstrumentDraft) => {
    const updated = [...instruments];
    updated[index] = entry;
    setInstruments(updated);
  };

  const handleInstrumentRemove = (index: number) => {
    setInstruments((prev) => prev.filter((_, i) => i !== index));
    setInstrumentKeys((prev) => prev.filter((_, i) => i !== index));
  };

  const handleInstrumentAdd = () => {
    setInstruments((prev) => [...prev, { instrument: "", people: "" }]);
    setInstrumentKeys((prev) => [...prev, keyCounter.current++]);
  };

  // 제출. 등록(POST)은 멱등이 아니라(같은 요청을 두 번 보내면 글이 두 개 생김) submitting 플래그로
  // 버튼을 잠가 연속 탭에 의한 중복 요청을 막는다(수정 PUT은 멱등이지만 같은 잠금을 쓴다).
  // 실패해도 이 화면에 그대로 남아 재시도할 수 있게 화면 이동은 onSubmit이 성공한 뒤에만 일어난다.
  const handleSubmit = async () => {
    if (submitting) return;
    setSubmitting(true);
    try {
      const payload: PostCreateRequest = {
        category,
        title,
        eventAt: `${eventDate}T${eventTime}:00`,
        location,
        region,
        timetable,
        description: description.trim() || undefined,
        instruments: instruments
          .filter((it) => it.instrument && it.people)
          .map(({ instrument, people }) => ({ instrument, people: Number(people) })),
      };
      await onSubmit(payload);
    } catch (err) {
      Alert.alert(
        isEdit ? "수정 실패" : "등록 실패",
        err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요."
      );
    } finally {
      setSubmitting(false);
    }
  };

  const submitTitle = submitting
    ? isEdit ? "저장 중..." : "등록 중..."
    : isEdit ? "저장하기" : "등록하기";

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => router.back()}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          accessibilityLabel="뒤로 가기"
        >
          <Ionicons name="arrow-back" size={22} color={colors.primary} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{isEdit ? "모집글 수정" : "모집글 작성"}</Text>
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
          <ChipSelect
            label="카테고리"
            options={CATEGORIES}
            selected={category}
            onSelect={setCategory}
          />

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>제목</Text>
            <TextInput
              style={styles.input}
              placeholder="모집글 제목을 입력하세요"
              placeholderTextColor={colors.placeholder}
              value={title}
              onChangeText={setTitle}
            />
          </View>

          <View style={styles.row}>
            <View style={[styles.fieldGroup, styles.rowField]}>
              <Text style={styles.label}>공연 날짜</Text>
              <TextInput
                style={styles.input}
                placeholder="2026-08-01"
                placeholderTextColor={colors.placeholder}
                value={eventDate}
                onChangeText={setEventDate}
              />
            </View>
            <View style={[styles.fieldGroup, styles.rowField]}>
              <Text style={styles.label}>공연 시간</Text>
              <TextInput
                style={styles.input}
                placeholder="14:00"
                placeholderTextColor={colors.placeholder}
                value={eventTime}
                onChangeText={setEventTime}
              />
            </View>
          </View>

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>장소</Text>
            <TextInput
              style={styles.input}
              placeholder="예: 서울 강남구 OO스튜디오"
              placeholderTextColor={colors.placeholder}
              value={location}
              onChangeText={setLocation}
            />
          </View>

          <ChipSelect
            label="지역"
            options={REGIONS}
            selected={region}
            onSelect={setRegion}
          />

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>시간표</Text>
            <TextInput
              style={[styles.input, styles.textArea]}
              placeholder="예: 매주 토요일 오후 2시 합주"
              placeholderTextColor={colors.placeholder}
              value={timetable}
              onChangeText={setTimetable}
              multiline
              numberOfLines={4}
              textAlignVertical="top"
            />
          </View>

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>설명 (선택)</Text>
            <TextInput
              style={[styles.input, styles.textArea]}
              placeholder="모집 목적, 분위기, 준비물 등을 자유롭게 적어주세요"
              placeholderTextColor={colors.placeholder}
              value={description}
              onChangeText={setDescription}
              multiline
              numberOfLines={4}
              textAlignVertical="top"
            />
          </View>

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>모집 악기</Text>
            {instruments.map((it, index) => (
              <PostInstrumentFormItem
                key={instrumentKeys[index]}
                value={it}
                index={index}
                onChange={handleInstrumentChange}
                onRemove={handleInstrumentRemove}
                removable={instruments.length > 1}
              />
            ))}
            <TouchableOpacity style={styles.addButton} onPress={handleInstrumentAdd}>
              <Ionicons name="add" size={18} color={colors.textMuted} />
              <Text style={styles.addButtonText}>악기 추가</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>

        {/* 하단 제출 버튼 */}
        <View style={[styles.footer, { paddingBottom: insets.bottom + 12 }]}>
          <ThemedButton title={submitTitle} onPress={handleSubmit} disabled={submitting} />
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
    gap: 8,
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
    marginBottom: 16,
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
    height: 90,
    paddingTop: 12,
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
  },
  addButtonText: {
    fontSize: 13,
    color: colors.textMuted,
  },
  footer: {
    paddingHorizontal: 24,
    paddingTop: 12,
    backgroundColor: colors.background,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
});
