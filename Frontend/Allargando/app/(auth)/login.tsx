import React, { useState } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  Image,
} from "react-native";
import { Link } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { useAuth } from "@/contexts/AuthContext";
import { describeAuthError } from "@/lib/authErrors";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";

const logoImg = require("@/assets/images/logo_image.png");

// 번들 에셋의 실제 픽셀 비율로 높이를 계산해 찌그러짐을 막는다
const { width: srcW, height: srcH } = Image.resolveAssetSource(logoImg);
const LOGO_WIDTH = 240;
const LOGO_HEIGHT = LOGO_WIDTH * (srcH / srcW);

export default function LoginScreen() {
  const { signIn, profilePending } = useAuth();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleLogin = async () => {
    if (isSubmitting) return;
    if (!loginId.trim() || !password) {
      Alert.alert("로그인", "아이디와 비밀번호를 입력해주세요.");
      return;
    }
    setIsSubmitting(true);
    try {
      await signIn(loginId, password);
      // 로그인 성공 시 RootLayout의 useAuth 구독이 자동으로 이동시킨다(프로필이 있으면 홈, 가입 미완료면 가입 이어하기)
    } catch (err) {
      // 계정 없음·비밀번호 오류는 같은 문구(Firebase 계정 열거 방지), 형식·네트워크·횟수 제한은 따로 안내
      Alert.alert("로그인 실패", describeAuthError(err, "login"));
    } finally {
      setIsSubmitting(false);
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
            label="아이디"
            icon="person-outline"
            placeholder="아이디를 입력하세요"
            value={loginId}
            onChangeText={setLoginId}
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

          {/* 아이디나 비밀번호를 잊은 경우 계정 찾기 화면(아이디 찾기·비밀번호 찾기)으로 보낸다 */}
          <Link href="/(auth)/find-account" asChild>
            <TouchableOpacity style={styles.forgotPassword}>
              <Text style={styles.forgotPasswordText}>아이디·비밀번호 찾기</Text>
            </TouchableOpacity>
          </Link>

          <ThemedButton title="로그인" onPress={handleLogin} loading={isSubmitting || profilePending} />
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
