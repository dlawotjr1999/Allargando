import React from "react";
import { ColorValue, StyleSheet, View } from "react-native";
import { Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";

// 탭 아이콘. 활성 탭은 위쪽에 벚꽃 분홍 점을 찍어 표시한다
function TabIcon({
  name,
  focusedName,
  focused,
  color,
  size,
}: {
  name: React.ComponentProps<typeof Ionicons>["name"];
  focusedName: React.ComponentProps<typeof Ionicons>["name"];
  focused: boolean;
  color: ColorValue;
  size: number;
}) {
  return (
    <View style={styles.iconWrap}>
      {focused && <View style={styles.dot} />}
      <Ionicons name={focused ? focusedName : name} size={size} color={color} />
    </View>
  );
}

export default function TabsLayout() {
  return (
    <Tabs
      initialRouteName="obri"
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.border,
          borderTopWidth: StyleSheet.hairlineWidth,
        },
        tabBarLabelStyle: {
          fontSize: 11,
          letterSpacing: 0.5,
        },
      }}
    >
      <Tabs.Screen
        name="practice-log"
        options={{
          title: "연습 일지",
          tabBarIcon: ({ color, focused, size }) => (
            <TabIcon name="musical-notes-outline" focusedName="musical-notes" focused={focused} color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="obri"
        options={{
          title: "홈",
          tabBarIcon: ({ color, focused, size }) => (
            <TabIcon name="home-outline" focusedName="home" focused={focused} color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="concerts"
        options={{
          title: "연주회",
          tabBarIcon: ({ color, focused, size }) => (
            <TabIcon name="disc-outline" focusedName="disc" focused={focused} color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="my-page"
        options={{
          title: "마이 페이지",
          tabBarIcon: ({ color, focused, size }) => (
            <TabIcon name="person-outline" focusedName="person" focused={focused} color={color} size={size} />
          ),
        }}
      />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  iconWrap: {
    alignItems: "center",
  },
  dot: {
    position: "absolute",
    top: -6,
    width: 5,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: colors.accent,
  },
});
