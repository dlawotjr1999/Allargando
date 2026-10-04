import React from "react";
import { View, TextInput, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { INSTRUMENTS } from "@/constants/filterOptions";
import ChipSelect from "@/components/common/ChipSelect";

export interface PostInstrumentDraft {
  instrument: string;
  people: string;
}

interface PostInstrumentFormItemProps {
  value: PostInstrumentDraft;
  index: number;
  onChange: (index: number, entry: PostInstrumentDraft) => void;
  onRemove: (index: number) => void;
  removable: boolean;
}

export default function PostInstrumentFormItem({
  value,
  index,
  onChange,
  onRemove,
  removable,
}: PostInstrumentFormItemProps) {
  return (
    <View style={styles.container}>
      {removable && (
        <TouchableOpacity
          style={styles.removeButton}
          onPress={() => onRemove(index)}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <Ionicons name="close-circle-outline" size={20} color={colors.textMuted} />
        </TouchableOpacity>
      )}
      <ChipSelect
        label="악기"
        options={INSTRUMENTS}
        selected={value.instrument}
        onSelect={(v) => onChange(index, { ...value, instrument: v })}
      />
      <TextInput
        style={styles.input}
        placeholder="모집 인원"
        placeholderTextColor={colors.placeholder}
        value={value.people}
        onChangeText={(text) => onChange(index, { ...value, people: text.replace(/[^0-9]/g, "") })}
        keyboardType="number-pad"
        maxLength={3}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 16,
  },
  removeButton: {
    alignSelf: "flex-end",
    marginBottom: 4,
  },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 0.5,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 14,
    height: 44,
    fontSize: 14,
    color: colors.textPrimary,
  },
});
