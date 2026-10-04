import React from "react";
import { View, TextInput, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { CareerEntry } from "@/types/user";
import { CAREER_MAX_LENGTH } from "@/utils/registerValidation";

interface CareerFormItemProps {
  value: CareerEntry;
  index: number;
  onChange: (index: number, entry: CareerEntry) => void;
  onRemove: (index: number) => void;
  removable: boolean;
}

export default function CareerFormItem({
  value,
  index,
  onChange,
  onRemove,
  removable,
}: CareerFormItemProps) {
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
      <TextInput
        style={styles.input}
        placeholder="예: 서울시립교향악단"
        placeholderTextColor={colors.placeholder}
        value={value.organization}
        onChangeText={(text) =>
          onChange(index, { ...value, organization: text })
        }
        maxLength={CAREER_MAX_LENGTH}
      />
      <TextInput
        style={[styles.input, styles.textArea]}
        placeholder="예: 2023-2024 객원 연주자"
        placeholderTextColor={colors.placeholder}
        value={value.contexts}
        onChangeText={(text) =>
          onChange(index, { ...value, contexts: text })
        }
        maxLength={CAREER_MAX_LENGTH}
        multiline
        textAlignVertical="top"
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
    marginBottom: 10,
  },
  textArea: {
    height: 80,
    paddingTop: 12,
  },
});
