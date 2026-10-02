import React from "react";
import { useRouter } from "expo-router";
import { createPost } from "@/api/post";
import PostForm from "@/components/post/PostForm";

// 모집글 작성 화면 — 폼은 수정 화면과 공용(PostForm). 성공하면 이전 화면으로 돌아간다.
export default function PostCreateScreen() {
  const router = useRouter();

  return (
    <PostForm
      mode="create"
      onSubmit={async (payload) => {
        await createPost(payload);
        router.back();
      }}
    />
  );
}
