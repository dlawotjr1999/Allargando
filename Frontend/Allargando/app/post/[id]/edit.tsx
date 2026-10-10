import React, { useEffect, useState } from "react";
import { View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useLocalSearchParams, useRouter } from "expo-router";
import { colors } from "@/constants/theme";
import { getPost, updatePost } from "@/api/post";
import { ApiError } from "@/lib/apiClient";
import { markPostListStale } from "@/lib/postListRefresh";
import { PostCreateRequest, PostDetail } from "@/types/post";
import EmptyState from "@/components/common/EmptyState";
import LoadingScreen from "@/components/common/LoadingScreen";
import ScreenHeader from "@/components/common/ScreenHeader";
import PostForm from "@/components/post/PostForm";

// 서버 응답(PostDetail)을 폼 초기값(등록·수정 공용 요청 바디 모양)으로 변환
function toFormValues(post: PostDetail): PostCreateRequest {
  return {
    category: post.category,
    title: post.title,
    eventAt: post.eventAt,
    location: post.location,
    region: post.region,
    timetable: post.timetable,
    description: post.description,
    instruments: post.instruments.map(({ instrument, people }) => ({ instrument, people })),
  };
}

// 모집글 수정 화면. 기존 글을 불러와 폼에 채우고, 저장하면 PUT으로 본문 전체를 교체한다(작성자만 — 서버가 검증).
// 작성자가 아닌 글로 들어오면(딥링크 등) 수정 폼을 열지 않는다.
export default function PostEditScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();

  const [post, setPost] = useState<PostDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // 오류 화면의 "다시 시도"가 올리는 번호. 올라가면 아래 효과가 글을 다시 불러온다
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    setLoading(true);
    setError(null);
    (async () => {
      try {
        setPost(await getPost(Number(id)));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "모집글을 불러오지 못했어요.");
      } finally {
        setLoading(false);
      }
    })();
  }, [id, reloadKey]);

  if (loading) return <LoadingScreen />;

  if (!post || !post.isMine) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }} edges={["top"]}>
        <View style={{ paddingHorizontal: 24, paddingTop: 8 }}>
          <ScreenHeader />
        </View>
        <EmptyState
          icon="alert-circle-outline"
          title="수정할 수 없는 모집글이에요"
          description={error ?? "내가 작성한 모집글만 수정할 수 있어요."}
          onRetry={error ? () => setReloadKey((k) => k + 1) : undefined}
        />
      </SafeAreaView>
    );
  }

  return (
    <PostForm
      mode="edit"
      initial={toFormValues(post)}
      onSubmit={async (payload) => {
        await updatePost(post.id, payload);
        markPostListStale();
        router.back();
      }}
    />
  );
}
