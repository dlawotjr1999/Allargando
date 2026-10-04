import React from "react";
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  TextInputProps,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";

interface ThemedInputProps extends TextInputProps {
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
  rightElement?: React.ReactNode;
  // 입력칸 아래 안내문. error가 있으면 안내문 대신 오류 문구(빨간색)를 보여준다
  hint?: string;
  error?: string | null;
}

export default function ThemedInput({
  label,
  icon,
  rightElement,
  hint,
  error,
  ...inputProps
}: ThemedInputProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.inputWrapper}>
        <Ionicons
          name={icon}
          size={18}
          color={colors.placeholder}
          style={styles.icon}
        />
        <TextInput
          style={styles.input}
          placeholderTextColor={colors.placeholder}
          {...inputProps}
        />
        {rightElement}
      </View>
      {error ? (
        <Text style={[styles.hint, styles.error]}>{error}</Text>
      ) : hint ? (
        <Text style={styles.hint}>{hint}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 16,
  },
  label: {
    fontSize: 12,
    color: colors.textSecondary,
    letterSpacing: 1,
    marginBottom: 6,
  },
  inputWrapper: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.surface,
    borderWidth: 0.5,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 14,
    height: 46,
  },
  icon: {
    marginRight: 10,
  },
  input: {
    flex: 1,
    fontSize: 14,
    color: colors.textPrimary,
  },
  hint: {
    fontSize: 11,
    color: colors.textMuted,
    marginTop: 6,
    lineHeight: 16,
  },
  error: {
    color: colors.danger,
  },
});
