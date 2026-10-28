import React, { useEffect, useState } from "react";
import { View, TextInput, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";

// 서버 검색어 최대 길이(백엔드 KeywordSearch.MAX_LENGTH)와 같다 — 넘으면 서버가 400을 준다
export const SEARCH_KEYWORD_MAX = 50;

interface SearchBarProps {
  // 지금 목록에 적용된 검색어. 바깥에서 바뀌면(예: 필터 전체 초기화) 입력칸도 따라 바뀐다
  value: string;
  // 검색 실행 — 앞뒤 공백을 지운 검색어를 넘긴다(지우기 버튼은 빈 문자열)
  onSearch: (keyword: string) => void;
  placeholder?: string;
}

// 목록 위의 검색창. 글자를 칠 때마다가 아니라 키보드의 검색 버튼을 눌렀을 때 검색해 요청 수를 줄인다(호출 빈도 제한).
// 연주회 목록이 쓰고, 모집글 목록 등 다른 목록도 그대로 가져다 쓸 수 있게 공통 컴포넌트로 둔다
export default function SearchBar({ value, onSearch, placeholder = "검색어를 입력하세요" }: SearchBarProps) {
  const [text, setText] = useState(value);

  // 적용된 검색어가 바깥에서 바뀌면 입력칸도 맞춘다
  useEffect(() => {
    setText(value);
  }, [value]);

  // 입력한 글자와 검색 결과를 함께 비운다
  const handleClear = () => {
    setText("");
    onSearch("");
  };

  return (
    <View style={styles.wrapper}>
      <Ionicons name="search" size={16} color={colors.placeholder} />
      <TextInput
        style={styles.input}
        value={text}
        onChangeText={setText}
        onSubmitEditing={() => onSearch(text.trim())}
        placeholder={placeholder}
        placeholderTextColor={colors.placeholder}
        returnKeyType="search"
        maxLength={SEARCH_KEYWORD_MAX}
        autoCorrect={false}
        autoCapitalize="none"
        accessibilityLabel="검색"
      />
      {text.length > 0 && (
        <TouchableOpacity
          onPress={handleClear}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          accessibilityLabel="검색어 지우기"
        >
          <Ionicons name="close-circle" size={16} color={colors.textMuted} />
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginHorizontal: 16,
    marginTop: 10,
    paddingHorizontal: 12,
    height: 40,
    borderRadius: 20,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  input: {
    flex: 1,
    fontSize: 14,
    color: colors.textPrimary,
    paddingVertical: 0,
  },
});
