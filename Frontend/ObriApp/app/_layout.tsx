import { Stack, Redirect } from "expo-router";
import { StatusBar } from "expo-status-bar";

import LoadingScreen from "@/components/common/LoadingScreen";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";

export default function RootLayout() {
  return (
    <AuthProvider>
      <RootNavigator />
    </AuthProvider>
  );
}

function RootNavigator() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <>
        <LoadingScreen />
        <StatusBar style="dark" />
      </>
    );
  }

  return (
    <>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(tabs)" />
      </Stack>
      <Redirect href={user ? "/(tabs)/home" : "/(auth)/login"} />
      <StatusBar style="dark" />
    </>
  );
}
