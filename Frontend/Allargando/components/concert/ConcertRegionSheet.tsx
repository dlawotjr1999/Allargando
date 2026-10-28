import React, { useState } from "react";
import { View, StyleSheet } from "react-native";
import { CONCERT_REGIONS } from "@/constants/filterOptions";
import BottomSheet from "@/components/common/BottomSheet";
import Chip from "@/components/common/Chip";

interface ConcertRegionSheetProps {
  visible: boolean;
  regions: string[];
  onApply: (regions: string[]) => void;
  onClose: () => void;
}

// 연주회 지역 선택 시트 — 여러 지역을 고를 수 있고(OR), 적용하기를 눌러야 목록에 반영된다
export default function ConcertRegionSheet({ visible, regions, onApply, onClose }: ConcertRegionSheetProps) {
  const [draft, setDraft] = useState<string[]>(regions);

  // 고른 지역이면 빼고, 아니면 더한다
  function toggle(region: string) {
    setDraft((prev) => (prev.includes(region) ? prev.filter((r) => r !== region) : [...prev, region]));
  }

  return (
    <BottomSheet
      visible={visible}
      title="지역"
      onClose={onClose}
      onShow={() => setDraft(regions)}
      onReset={() => setDraft([])}
      onApply={() => {
        onApply(draft);
        onClose();
      }}
    >
      <View style={styles.chipGroup}>
        {CONCERT_REGIONS.map((region) => (
          <Chip key={region} label={region} active={draft.includes(region)} onPress={() => toggle(region)} />
        ))}
      </View>
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  chipGroup: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 8,
  },
});
