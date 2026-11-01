import React, { useState } from "react";
import { View, StyleSheet } from "react-native";
import { DATE_PRESET_LABELS, DatePreset, matchPreset, parseDate, presetRange } from "@/utils/datetime";
import BottomSheet from "@/components/common/BottomSheet";
import Chip from "@/components/common/Chip";
import DateTimeField from "@/components/common/DateTimeField";

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
        <View style={[styles.customArea, styles.row]}>
          {/* 이미 끝난 공연은 목록에 없으므로 오늘 이전은 고를 필요가 없다 */}
          <DateTimeField
            mode="date"
            label="시작일"
            value={draft.fromDate ?? ""}
            onChange={(value) => setDraft((prev) => ({ ...prev, fromDate: value }))}
            placeholder="선택 안 함"
            minimumDate={new Date()}
            maximumDate={draft.toDate ? parseDate(draft.toDate) : undefined}
            style={styles.rowField}
          />
          <DateTimeField
            mode="date"
            label="종료일"
            value={draft.toDate ?? ""}
            onChange={(value) => setDraft((prev) => ({ ...prev, toDate: value }))}
            placeholder="선택 안 함"
            minimumDate={draft.fromDate ? parseDate(draft.fromDate) : new Date()}
            style={styles.rowField}
          />
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
  rowField: {
    flex: 1,
  },
});
