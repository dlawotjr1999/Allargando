import React, { useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import DateTimePicker from "@react-native-community/datetimepicker";
import { colors } from "@/constants/theme";
import {
  DATE_PRESET_LABELS,
  DatePreset,
  formatDate,
  matchPreset,
  parseDate,
  presetRange,
  toDateOnly,
} from "@/utils/datetime";
import BottomSheet from "@/components/common/BottomSheet";
import Chip from "@/components/common/Chip";

interface DateRange {
  fromDate?: string;
  toDate?: string;
}

interface ConcertDateSheetProps extends DateRange {
  visible: boolean;
  onApply: (range: DateRange) => void;
  onClose: () => void;
}

// 시트 안에서 고른 방식 — 빠른 선택(오늘·이번 주말·이번 달), 직접 선택, 또는 아무것도 안 고름(null)
type Mode = DatePreset | "custom" | null;

const PRESETS: DatePreset[] = ["today", "weekend", "month"];

// 연주회 기간 선택 시트. 자주 쓰는 기간은 한 번에 고르고, 그 밖의 기간은 "직접 선택"에서 날짜를 고른다.
// 서버는 이 기간에 공연 기간이 겹치는(그 기간에 볼 수 있는) 공연을 돌려준다
export default function ConcertDateSheet({ visible, fromDate, toDate, onApply, onClose }: ConcertDateSheetProps) {
  const [draft, setDraft] = useState<DateRange>({ fromDate, toDate });
  const [mode, setMode] = useState<Mode>(null);
  const [showFromPicker, setShowFromPicker] = useState(false);
  const [showToPicker, setShowToPicker] = useState(false);

  // 열릴 때 적용된 기간으로 맞추고, 그 기간이 빠른 선택과 같으면 그 칩을, 아니면 직접 선택을 켠다
  function handleShow() {
    setDraft({ fromDate, toDate });
    setMode(matchPreset(fromDate, toDate) ?? (fromDate || toDate ? "custom" : null));
  }

  // 빠른 선택 — 오늘 기준으로 기간을 계산해 바로 채운다
  function selectPreset(preset: DatePreset) {
    setMode(preset);
    setDraft(presetRange(preset));
  }

  return (
    <BottomSheet
      visible={visible}
      title="날짜"
      onClose={onClose}
      onShow={handleShow}
      onReset={() => {
        setMode(null);
        setDraft({});
      }}
      onApply={() => {
        onApply(draft);
        onClose();
      }}
    >
      <View style={styles.chipGroup}>
        {PRESETS.map((preset) => (
          <Chip
            key={preset}
            label={DATE_PRESET_LABELS[preset]}
            active={mode === preset}
            onPress={() => selectPreset(preset)}
          />
        ))}
        <Chip label="직접 선택" active={mode === "custom"} onPress={() => setMode("custom")} />
      </View>

      {mode === "custom" && (
        <View style={styles.customArea}>
          <View style={styles.row}>
            <TouchableOpacity style={styles.field} onPress={() => setShowFromPicker(true)}>
              <Text style={styles.fieldLabel}>시작일</Text>
              <Text style={[styles.fieldValue, !draft.fromDate && styles.fieldPlaceholder]}>
                {draft.fromDate ? formatDate(draft.fromDate) : "선택 안 함"}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.field} onPress={() => setShowToPicker(true)}>
              <Text style={styles.fieldLabel}>종료일</Text>
              <Text style={[styles.fieldValue, !draft.toDate && styles.fieldPlaceholder]}>
                {draft.toDate ? formatDate(draft.toDate) : "선택 안 함"}
              </Text>
            </TouchableOpacity>
          </View>
          {/* 이미 끝난 공연은 목록에 없으므로 오늘 이전은 고를 필요가 없다 */}
          {showFromPicker && (
            <DateTimePicker
              value={draft.fromDate ? parseDate(draft.fromDate) : new Date()}
              mode="date"
              minimumDate={new Date()}
              maximumDate={draft.toDate ? parseDate(draft.toDate) : undefined}
              onChange={(event, selected) => {
                setShowFromPicker(false);
                if (event.type === "set" && selected) setDraft((prev) => ({ ...prev, fromDate: toDateOnly(selected) }));
              }}
            />
          )}
          {showToPicker && (
            <DateTimePicker
              value={draft.toDate ? parseDate(draft.toDate) : draft.fromDate ? parseDate(draft.fromDate) : new Date()}
              mode="date"
              minimumDate={draft.fromDate ? parseDate(draft.fromDate) : new Date()}
              onChange={(event, selected) => {
                setShowToPicker(false);
                if (event.type === "set" && selected) setDraft((prev) => ({ ...prev, toDate: toDateOnly(selected) }));
              }}
            />
          )}
        </View>
      )}
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  chipGroup: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  customArea: {
    marginTop: 16,
    marginBottom: 8,
  },
  row: {
    flexDirection: "row",
    gap: 12,
  },
  field: {
    flex: 1,
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
    gap: 4,
  },
  fieldLabel: {
    fontSize: 11,
    color: colors.textMuted,
  },
  fieldValue: {
    fontSize: 14,
    color: colors.textPrimary,
  },
  fieldPlaceholder: {
    color: colors.placeholder,
  },
});
