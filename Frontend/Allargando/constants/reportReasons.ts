import { ReportReason } from "@/types/safety";

// 신고 화면의 사유 선택지(표시 문구). 값은 백엔드 ReportReason enum과 같아야 한다.
export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: "SPAM", label: "스팸·광고" },
  { value: "INAPPROPRIATE", label: "부적절한 내용" },
  { value: "FRAUD", label: "사기·허위 정보" },
  { value: "HARASSMENT", label: "괴롭힘·혐오 표현" },
  { value: "OTHER", label: "기타" },
];
