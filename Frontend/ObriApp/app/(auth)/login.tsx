import React, { useState } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Alert,
  Image,
} from "react-native";
import { Link } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { useAuth } from "@/contexts/AuthContext";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";

const logoImg = require("@/assets/images/logo_image.png");

// 번들 에셋의 실제 픽셀 비율로 높이를 계산해 찌그러짐을 막는다
const { width: srcW, height: srcH } = Image.resolveAssetSource(logoImg);
const LOGO_WIDTH = 240;
const LOGO_HEIGHT = LOGO_WIDTH * (srcH / srcW);

export default function LoginScreen() {
  const { signIn, resetPassword } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleLogin = async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      await signIn(email, password);
      // 로그인 성공 시 RootLayout의 useAuth 구독이 자동으로 (tabs)로 리다이렉트
    } catch {
      Alert.alert("로그인 실패", "이메일 또는 비밀번호를 확인해주세요.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // 비밀번호 재설정 메일 발송 — 위 이메일 입력칸 값을 그대로 쓴다.
  // 가입 여부를 노출하지 않도록 성공 안내는 "가입된 이메일이라면"으로 통일한다.
  const handleForgotPassword = async () => {
    const trimmed = email.trim();
    if (!trimmed) {
      Alert.alert("이메일 입력", "비밀번호를 재설정할 이메일을 먼저 입력해주세요.");
      return;
    }
    try {
      await resetPassword(trimmed);
      Alert.alert("메일을 보냈어요", "가입된 이메일이라면 비밀번호 재설정 메일이 도착합니다.");
    } catch {
      Alert.alert("발송 실패", "이메일 형식을 확인하거나 잠시 후 다시 시도해주세요.");
    }
  };

  return (
    <View style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
      >
        <View style={styles.logoArea}>
          <Image
            source={logoImg}
            style={{ width: LOGO_WIDTH, height: LOGO_HEIGHT, alignSelf: "center" }}
            resizeMode="contain"
          />
        </View>

        <View style={styles.form}>
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

          <ThemedInput
            label="비밀번호"
            icon="lock-closed-outline"
            placeholder="비밀번호를 입력하세요"
            value={password}
            onChangeText={setPassword}
            secureTextEntry={!showPassword}
            rightElement={
              <TouchableOpacity
                onPress={() => setShowPassword(!showPassword)}
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              >
                <Ionicons
                  name={showPassword ? "eye-off-outline" : "eye-outline"}
                  size={18}
                  color={colors.placeholder}
                />
              </TouchableOpacity>
            }
          />

          <TouchableOpacity style={styles.forgotPassword} onPress={handleForgotPassword}>
            <Text style={styles.forgotPasswordText}>비밀번호를 잊으셨나요?</Text>
          </TouchableOpacity>

          <ThemedButton title="로그인" onPress={handleLogin} loading={isSubmitting} />
        </View>

        <View style={styles.signupRow}>
          <Text style={styles.signupText}>계정이 없으신가요? </Text>
          <Link href="/(auth)/register" asChild>
            <TouchableOpacity>
              <Text style={styles.signupLink}>회원가입</Text>
            </TouchableOpacity>
          </Link>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: 32,
  },
  logoArea: {
    marginTop: 120,
    marginBottom: 48,
  },
  form: {
    width: "100%",
  },
  forgotPassword: {
    alignSelf: "flex-end",
    marginBottom: 32,
    marginTop: 2,
  },
  forgotPasswordText: {
    fontSize: 12,
    color: colors.textMuted,
  },
  signupRow: {
    flexDirection: "row",
    justifyContent: "center",
    marginTop: "auto",
    marginBottom: 40,
  },
  signupText: {
    fontSize: 13,
    color: colors.textMuted,
  },
  signupLink: {
    fontSize: 13,
    color: colors.primary,
    fontWeight: "500",
  },
});
