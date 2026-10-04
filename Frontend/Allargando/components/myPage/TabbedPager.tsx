import React, { useRef, useState } from "react";
import {
  View,
  ScrollView,
  Text,
  TouchableOpacity,
  StyleSheet,
  Animated,
  NativeSyntheticEvent,
  NativeScrollEvent,
  useWindowDimensions,
} from "react-native";
import { colors } from "@/constants/theme";

interface TabbedPagerProps {
  tabs: { key: string; label: string }[];
  pages: React.ReactNode[];
}

export default function TabbedPager({ tabs, pages }: TabbedPagerProps) {
  const { width: screenWidth } = useWindowDimensions();
  const pageRef = useRef<ScrollView>(null);
  const indicatorAnim = useRef(new Animated.Value(0)).current;
  const [activeIndex, setActiveIndex] = useState(0);

  const tabWidth = (screenWidth - 32) / tabs.length;

  function moveIndicator(index: number) {
    Animated.spring(indicatorAnim, {
      toValue: index * tabWidth,
      useNativeDriver: true,
      tension: 180,
      friction: 20,
    }).start();
  }

  function onTabPress(index: number) {
    setActiveIndex(index);
    pageRef.current?.scrollTo({ x: index * screenWidth, animated: true });
    moveIndicator(index);
  }

  function onPageScroll(e: NativeSyntheticEvent<NativeScrollEvent>) {
    const x = e.nativeEvent.contentOffset.x;
    const index = Math.round(x / screenWidth);
    if (index !== activeIndex && index >= 0 && index < tabs.length) {
      setActiveIndex(index);
      moveIndicator(index);
    }
  }

  return (
    <View>
      <View style={styles.tabBar}>
        {tabs.map((tab, index) => (
          <TouchableOpacity
            key={tab.key}
            style={styles.tab}
            onPress={() => onTabPress(index)}
            activeOpacity={0.7}
          >
            <Text style={[styles.tabText, activeIndex === index && styles.tabTextActive]}>
              {tab.label}
            </Text>
          </TouchableOpacity>
        ))}
        <Animated.View
          style={[
            styles.tabIndicator,
            { width: tabWidth, transform: [{ translateX: indicatorAnim }] },
          ]}
        />
      </View>

      <ScrollView
        ref={pageRef}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        scrollEventThrottle={16}
        onScroll={onPageScroll}
        style={styles.pager}
      >
        {pages.map((page, index) => (
          <View key={tabs[index].key} style={[styles.page, { width: screenWidth }]}>
            {page}
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    flexDirection: "row",
    marginHorizontal: 16,
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 10,
    overflow: "hidden",
    position: "relative",
  },
  tab: {
    flex: 1,
    paddingVertical: 11,
    alignItems: "center",
  },
  tabText: {
    fontSize: 14,
    fontWeight: "500",
    color: colors.textMuted,
  },
  tabTextActive: {
    color: colors.primary,
    fontWeight: "700",
  },
  tabIndicator: {
    position: "absolute",
    bottom: 0,
    left: 0,
    height: 2,
    backgroundColor: colors.primary,
    borderRadius: 1,
  },
  pager: {
    marginTop: 12,
    minHeight: 400,
  },
  page: {
    padding: 16,
    paddingTop: 4,
  },
});
