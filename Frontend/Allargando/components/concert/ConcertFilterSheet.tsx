import React, { useState } from "react";
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import DateTimePicker from "@react-native-community/datetimepicker";
import { colors } from "@/constants/theme";
import { CONCERT_REGIONS } from "@/constants/filterOptions";
import { ConcertFilter } from "@/types/concertFilter";
import { formatDate, parseDate, toDateOnly } from "@/utils/datetime";
import Chip from "@/components/common/Chip";

interface ConcertFilterSheetProps {
  visible: boolean;
  filter: ConcertFilter;
  onApply: (filter: ConcertFilter) => void;
  onClose: () => void;
}

// Post의 FilterSheet와 같은 구조지만, 악기·상태 섹션이 없어 지역·기간 2개만 둔다
export default function ConcertFilterSheet({ visible, filter, onApply, onClose }: ConcertFilterSheetProps) {
  const insets = useSafeAreaInsets();
  const [draft, setDraft] = useState<ConcertFilter>(filter);
  const [showFromPicker, setShowFromPicker] = useState(false);
  const [showToPicker, setShowToPicker] = useState(false);

  function toggleRegion(region: string) {
    setDraft((prev) => ({
      ...prev,
      regions: prev.regions.includes(region)
        ? prev.regions.filter((r) => r !== region)
        : [...prev.regions, region],
    }));
  }

  function handleApply() {
    onApply(draft);
    onClose();
  }

  function handleReset() {
    setDraft({ ...filter, regions: [], fromDate: undefined, toDate: undefined });
  }

  // 모달이 열릴 때마다 draft를 현재 filter로 동기화
  function handleShow() {
    setDraft(filter);
  }

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onShow={handleShow}
      onRequestClose={onClose}
    >
      <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={onClose} />

      <View style={[styles.sheet, { paddingBottom: insets.bottom + 16 }]}>
        <View style={styles.sheetHeader}>
          <Text style={styles.sheetTitle}>필터</Text>
          <TouchableOpacity onPress={onClose} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
            <Ionicons name="close" size={22} color={colors.primary} />
          </TouchableOpacity>
        </View>

        <ScrollView showsVerticalScrollIndicator={false}>
          <Section title="지역">
            <View style={chipGroupStyle}>
              {CONCERT_REGIONS.map((region) => (
                <Chip
                  key={region}
                  label={region}
                  active={draft.regions.includes(region)}
                  onPress={() => toggleRegion(region)}
                />
              ))}
            </View>
          </Section>

          <Section title="기간">
            <View style={dateStyles.row}>
              <TouchableOpacity style={dateStyles.field} onPress={() => setShowFromPicker(true)}>
                <Text style={dateStyles.fieldLabel}>시작일</Text>
                <Text style={[dateStyles.fieldValue, !draft.fromDate && dateStyles.fieldPlaceholder]}>
                  {draft.fromDate ? formatDate(draft.fromDate) : "선택 안 함"}
                </Text>
              </TouchableOpacity>
              <TouchableOpacity style={dateStyles.field} onPress={() => setShowToPicker(true)}>
                <Text style={dateStyles.fieldLabel}>종료일</Text>
                <Text style={[dateStyles.fieldValue, !draft.toDate && dateStyles.fieldPlaceholder]}>
                  {draft.toDate ? formatDate(draft.toDate) : "선택 안 함"}
                </Text>
              </TouchableOpacity>
            </View>
            {(draft.fromDate || draft.toDate) && (
              <TouchableOpacity
                style={dateStyles.clearButton}
                onPress={() => setDraft({ ...draft, fromDate: undefined, toDate: undefined })}
              >
                <Text style={dateStyles.clearText}>기간 초기화</Text>
              </TouchableOpacity>
            )}
            {showFromPicker && (
              <DateTimePicker
                value={draft.fromDate ? parseDate(draft.fromDate) : new Date()}
                mode="date"
                maximumDate={draft.toDate ? parseDate(draft.toDate) : undefined}
                onChange={(event, selected) => {
                  setShowFromPicker(false);
                  if (event.type === "set" && selected) {
                    setDraft({ ...draft, fromDate: toDateOnly(selected) });
                  }
                }}
              />
            )}
            {showToPicker && (
              <DateTimePicker
                value={draft.toDate ? parseDate(draft.toDate) : new Date()}
                mode="date"
                minimumDate={draft.fromDate ? parseDate(draft.fromDate) : undefined}
                onChange={(event, selected) => {
                  setShowToPicker(false);
                  if (event.type === "set" && selected) {
                    setDraft({ ...draft, toDate: toDateOnly(selected) });
                  }
                }}
              />
            )}
          </Section>
        </ScrollView>

        <View style={styles.footer}>
          <TouchableOpacity style={styles.resetButton} onPress={handleReset} activeOpacity={0.7}>
            <Text style={styles.resetText}>초기화</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.applyButton} onPress={handleApply} activeOpacity={0.85}>
            <Text style={styles.applyText}>적용하기</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={sectionStyles.container}>
      <Text style={sectionStyles.title}>{title}</Text>
      {children}
    </View>
  );
}

const chipGroupStyle = { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 8 };

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.3)",
  },
  sheet: {
    backgroundColor: colors.background,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: "75%",
    paddingTop: 20,
    paddingHorizontal: 20,
  },
  sheetHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 20,
  },
  sheetTitle: {
    fontSize: 17,
    fontWeight: "700",
    color: colors.primary,
  },
  footer: {
    flexDirection: "row",
    gap: 12,
    marginTop: 16,
  },
  resetButton: {
    flex: 1,
    height: 48,
    borderRadius: 24,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
  },
  resetText: {
    fontSize: 14,
    color: colors.textSecondary,
  },
  applyButton: {
    flex: 2,
    height: 48,
    borderRadius: 24,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  applyText: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.background,
    letterSpacing: 0.5,
  },
});

const sectionStyles = StyleSheet.create({
  container: {
    marginBottom: 24,
  },
  title: {
    fontSize: 12,
    color: colors.textMuted,
    letterSpacing: 1,
    marginBottom: 12,
  },
});

const dateStyles = StyleSheet.create({
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
  clearButton: {
    alignSelf: "flex-start",
    marginTop: 10,
  },
  clearText: {
    fontSize: 12,
    color: colors.textMuted,
    textDecorationLine: "underline",
  },
});
