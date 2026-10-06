import React, { useState } from "react";
import { View, Text, StyleSheet, Alert } from "react-native";
import { colors } from "@/constants/theme";
import { useAuth } from "@/contexts/AuthContext";
import { describeAuthError } from "@/lib/authErrors";
import { isEmailFormat } from "@/utils/registerValidation";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";

// 비밀번호 찾기(이메일). 이메일을 입력하면 Firebase가 비밀번호 재설정 메일을 보내고, 사용자는 메일의 링크에서 새
// 비밀번호를 정한다. 가입 여부를 추측하는 데 쓰이지 않도록 가입된 이메일이든 아니든 같은 안내를 보여 준다.
export default function ResetPasswordByEmailSection() {
  const { resetPassword } = useAuth();
  const [email, setEmail] = useState("");
  const [sending, setSending] = useState(false);

  // 형식을 먼저 확인하고 재설정 메일을 보낸다. 연속 탭으로 여러 통이 나가지 않도록 보내는 동안 버튼을 잠근다
  const handleSend = async () => {
    const trimmed = email.trim();
    if (!trimmed) {
      Alert.alert("이메일 입력", "가입할 때 쓴 이메일을 입력해 주세요.");
      return;
    }
    if (!isEmailFormat(trimmed)) {
      Alert.alert("이메일 확인", "이메일 형식을 확인해 주세요.");
      return;
    }
    setSending(true);
    try {
      await resetPassword(trimmed);
      Alert.alert(
        "메일을 보냈어요",
        "가입된 이메일이라면 비밀번호 재설정 메일이 도착해요. 메일이 보이지 않으면 스팸함도 확인해 주세요."
      );
    } catch (err) {
      Alert.alert("메일을 보내지 못했어요", describeAuthError(err, "reset"));
    } finally {
      setSending(false);
    }
  };

  return (
    <View>
      <Text style={styles.description}>
        가입할 때 쓴 이메일로 비밀번호 재설정 메일을 보내드려요. 메일의 링크에서 새 비밀번호를 정해 주세요.
      </Text>
      <ThemedInput
        label="이메일"
        icon="mail-outline"
        placeholder="example@email.com"
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
        autoCorrect={false}
      />
      <ThemedButton title="재설정 메일 보내기" onPress={handleSend} loading={sending} />
    </View>
  );
}

const styles = StyleSheet.create({
  description: {
    fontSize: 13,
    color: colors.textMuted,
    lineHeight: 19,
    marginBottom: 20,
  },
});
