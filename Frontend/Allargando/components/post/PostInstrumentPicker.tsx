import React from "react";
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { INSTRUMENTS } from "@/constants/filterOptions";
import { POST_INSTRUMENTS_MAX, POST_PEOPLE_MAX, POST_PEOPLE_MIN } from "@/utils/postValidation";
import ChipSelect from "@/components/common/ChipSelect";

export interface PostInstrumentDraft {
  instrument: string;
  people: string; // 입력 중에는 비어 있을 수 있어 문자열로 둔다
}

interface PostInstrumentPickerProps {
  items: PostInstrumentDraft[];
  onChange: (items: PostInstrumentDraft[]) => void;
  // 수정 화면에서 악기별로 이미 수락된 인원. 이 밑으로 줄이거나 수락자가 있는 악기를 빼면 서버가 거절하므로(D12) 미리 막는다
  confirmedByInstrument?: Record<string, number>;
}

// 모집 악기·인원 선택. 칩을 누르면 아래 목록에 1명으로 추가되고 다시 누르면 빠진다.
// 인원은 − + 로 조절하고, 많을 때(예: 바이올린 12명)는 가운데 숫자를 눌러 직접 입력할 수 있다.
// 한 악기는 칩이 하나뿐이라 같은 악기를 두 번 넣을 수 없다
export default function PostInstrumentPicker({
  items,
  onChange,
  confirmedByInstrument = {},
}: PostInstrumentPickerProps) {
  const selected = items.map((it) => it.instrument);

  // 그 악기의 최소 인원 — 수락된 인원이 있으면 그 수까지만 줄일 수 있다
  const minPeople = (instrument: string) => Math.max(POST_PEOPLE_MIN, confirmedByInstrument[instrument] ?? 0);

  // 한 악기의 인원만 바꾼 목록을 올린다
  const setPeople = (instrument: string, people: string) => {
    onChange(items.map((it) => (it.instrument === instrument ? { ...it, people } : it)));
  };

  // 수락자가 있는 악기는 빼지 않고 이유를 알려 준다
  const remove = (instrument: string) => {
    if ((confirmedByInstrument[instrument] ?? 0) > 0) {
      Alert.alert("뺄 수 없는 악기예요", `${instrument}에 이미 수락된 인원이 있어요.`);
      return;
    }
    onChange(items.filter((it) => it.instrument !== instrument));
  };

  // 칩을 눌렀을 때 — 고른 악기면 빼고, 아니면 1명으로 추가한다(모집 악기 수 상한 확인)
  const toggle = (instrument: string) => {
    if (selected.includes(instrument)) {
      remove(instrument);
    } else if (items.length >= POST_INSTRUMENTS_MAX) {
      Alert.alert("악기를 더 고를 수 없어요", `모집 악기는 ${POST_INSTRUMENTS_MAX}개까지 고를 수 있어요.`);
    } else {
      onChange([...items, { instrument, people: String(POST_PEOPLE_MIN) }]);
    }
  };

  // 인원을 허용 범위 안의 가까운 값으로 맞춰 저장한다 — − + 버튼과 직접 입력 완료(비었거나 범위 밖인 값)가 함께 쓴다
  const applyPeople = (instrument: string, people: number) => {
    setPeople(instrument, String(Math.min(POST_PEOPLE_MAX, Math.max(minPeople(instrument), people))));
  };

  return (
    <View>
      <ChipSelect label="모집 악기" options={INSTRUMENTS} selected={selected} onSelect={toggle} />

      {items.map((item) => {
        const people = Number(item.people) || 0;
        const min = minPeople(item.instrument);
        const confirmed = confirmedByInstrument[item.instrument] ?? 0;
        return (
          <View key={item.instrument} style={styles.row}>
            <View style={styles.nameArea}>
              <Text style={styles.name} numberOfLines={1}>
                {item.instrument}
              </Text>
              {confirmed > 0 && <Text style={styles.confirmed}>수락 {confirmed}명</Text>}
            </View>

            <TouchableOpacity
              style={[styles.stepButton, people <= min && styles.stepButtonDisabled]}
              onPress={() => applyPeople(item.instrument, people - 1)}
              disabled={people <= min}
              accessibilityRole="button"
              accessibilityLabel={`${item.instrument} 인원 줄이기`}
            >
              <Ionicons name="remove" size={18} color={colors.primary} />
            </TouchableOpacity>
            <TextInput
              style={styles.peopleInput}
              value={item.people}
              onChangeText={(text) => setPeople(item.instrument, text.replace(/[^0-9]/g, ""))}
              onEndEditing={() => applyPeople(item.instrument, people)}
              keyboardType="number-pad"
              maxLength={3}
              selectTextOnFocus
              accessibilityLabel={`${item.instrument} 모집 인원`}
            />
            <TouchableOpacity
              style={[styles.stepButton, people >= POST_PEOPLE_MAX && styles.stepButtonDisabled]}
              onPress={() => applyPeople(item.instrument, people + 1)}
              disabled={people >= POST_PEOPLE_MAX}
              accessibilityRole="button"
              accessibilityLabel={`${item.instrument} 인원 늘리기`}
            >
              <Ionicons name="add" size={18} color={colors.primary} />
            </TouchableOpacity>

            {/* 수락자가 있는 악기는 뺄 수 없어 버튼을 두지 않되, 줄 끝이 맞도록 자리는 남긴다 */}
            {confirmed > 0 ? (
              <View style={styles.removePlaceholder} />
            ) : (
              <TouchableOpacity
                style={styles.removeButton}
                onPress={() => remove(item.instrument)}
                hitSlop={{ top: 10, bottom: 10, left: 6, right: 6 }}
                accessibilityRole="button"
                accessibilityLabel={`${item.instrument} 빼기`}
              >
                <Ionicons name="close-circle-outline" size={20} color={colors.textMuted} />
              </TouchableOpacity>
            )}
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 10,
    paddingLeft: 14,
    paddingRight: 8,
    paddingVertical: 8,
    marginTop: 8,
  },
  nameArea: {
    flex: 1,
  },
  name: {
    fontSize: 14,
    color: colors.textPrimary,
  },
  confirmed: {
    fontSize: 11,
    color: colors.textMuted,
    marginTop: 2,
  },
  stepButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    backgroundColor: colors.background,
    alignItems: "center",
    justifyContent: "center",
  },
  stepButtonDisabled: {
    opacity: 0.35,
  },
  peopleInput: {
    width: 40,
    paddingVertical: 4,
    textAlign: "center",
    fontSize: 15,
    fontWeight: "600",
    color: colors.textPrimary,
  },
  removeButton: {
    width: 24,
    alignItems: "center",
  },
  removePlaceholder: {
    width: 24,
  },
});
