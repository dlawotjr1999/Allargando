// 날짜/시간 포맷 유틸. 순수 함수만 모음 (외부 의존 없음).

const DATE_ONLY_RE = /^\d{4}-\d{2}-\d{2}$/;

// "YYYY-MM-DD"(시간 없는 순수 날짜)는 new Date()가 UTC 자정으로 해석한다
// (ECMA-262 date-only 규칙). UTC보다 느린 타임존(미주 등)에서는 이 때문에
// 하루 전날로 밀려 보인다. 반면 이 파일의 다른 필드(eventAt 등)는
// 타임존 없는 전체 datetime("...T10:00:00")이라 로컬로 파싱돼 문제가 없다.
// 콩쿠르 deadline/startDate/endDate처럼 순수 날짜 문자열만 로컬 자정으로
// 명시 파싱해 두 포맷이 같은 함수를 타도 동일하게 동작하도록 맞춘다.
export function parseDate(iso: string): Date {
  if (DATE_ONLY_RE.test(iso)) {
    const [y, m, d] = iso.split("-").map(Number);
    return new Date(y, m - 1, d);
  }
  return new Date(iso);
}

/**
 * ISO 8601 문자열을 받아 오늘 기준 D-day 문자열 반환.
 * D-day → "D-day" / D-3 → "D-3" / 지난 날짜 → "D+1"
 */
export function getDday(iso: string): { label: string; urgent: boolean; expired: boolean } {
  const event = parseDate(iso);
  if (Number.isNaN(event.getTime())) return { label: "", urgent: false, expired: false };

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  event.setHours(0, 0, 0, 0);

  const diff = Math.round((event.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));

  if (diff === 0) return { label: "D-day", urgent: true, expired: false };
  if (diff > 0) return { label: `D-${diff}`, urgent: diff <= 3, expired: false };
  return { label: `D+${Math.abs(diff)}`, urgent: false, expired: true };
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

const pad = (n: number) => String(n).padStart(2, "0");

// Date → "YYYY-MM-DD" (parseDate의 역변환, 날짜 선택 UI 등에서 사용)
export function toDateOnly(date: Date): string {
  const y = date.getFullYear();
  const m = pad(date.getMonth() + 1);
  const d = pad(date.getDate());
  return `${y}-${m}-${d}`;
}

// "YYYY-MM-DD" → "MM.DD" (필터 칩처럼 좁은 자리에 기간을 보일 때)
export function formatShortDate(iso: string): string {
  const date = parseDate(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return `${pad(date.getMonth() + 1)}.${pad(date.getDate())}`;
}

// 기간 빠른 선택. 기간은 시작·끝 날짜를 모두 포함한다
export type DatePreset = "today" | "weekend" | "month";

export const DATE_PRESET_LABELS: Record<DatePreset, string> = {
  today: "오늘",
  weekend: "이번 주말",
  month: "이번 달",
};

// 오늘 기준 빠른 선택 기간을 "YYYY-MM-DD"로 계산한다. 주말은 토·일 — 토요일이면 오늘~내일, 일요일이면 오늘 하루.
// 이번 달은 오늘부터 말일까지(지난 날짜는 볼 필요가 없다)
export function presetRange(preset: DatePreset, now: Date = new Date()): { fromDate: string; toDate: string } {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const offset = (days: number) => new Date(today.getFullYear(), today.getMonth(), today.getDate() + days);
  switch (preset) {
    case "today":
      return { fromDate: toDateOnly(today), toDate: toDateOnly(today) };
    case "weekend": {
      const day = today.getDay(); // 0=일 … 6=토
      if (day === 0) return { fromDate: toDateOnly(today), toDate: toDateOnly(today) };
      return { fromDate: toDateOnly(offset(6 - day)), toDate: toDateOnly(offset(7 - day)) };
    }
    case "month":
      return {
        fromDate: toDateOnly(today),
        toDate: toDateOnly(new Date(today.getFullYear(), today.getMonth() + 1, 0)),
      };
  }
}

// 고른 기간이 오늘 기준 빠른 선택 중 하나와 같으면 그 이름을, 아니면 null (칩·시트에 어떤 선택인지 다시 보여 줄 때)
export function matchPreset(fromDate?: string, toDate?: string): DatePreset | null {
  if (!fromDate || !toDate) return null;
  const presets: DatePreset[] = ["today", "weekend", "month"];
  return (
    presets.find((preset) => {
      const range = presetRange(preset);
      return range.fromDate === fromDate && range.toDate === toDate;
    }) ?? null
  );
}

export function formatDate(iso: string): string {
  const date = parseDate(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const y = date.getFullYear();
  const m = pad(date.getMonth() + 1);
  const d = pad(date.getDate());
  const weekday = WEEKDAYS[date.getDay()];
  return `${y}.${m}.${d} (${weekday})`;
}

export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes}분`;
  const h = Math.floor(minutes / 60);
  const rem = minutes % 60;
  return rem > 0 ? `${h}시간 ${rem}분` : `${h}시간`;
}

/**
 * ISO 8601 문자열을 "YYYY.MM.DD (요일) HH:mm" 형태로 포맷.
 * 예: "2024-05-01T14:00:00" -> "2024.05.01 (수) 14:00"
 * 파싱 불가한 값이면 원본 문자열을 그대로 반환.
 */
export function formatEventDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;

  const y = date.getFullYear();
  const m = pad(date.getMonth() + 1);
  const d = pad(date.getDate());
  const weekday = WEEKDAYS[date.getDay()];
  const hh = pad(date.getHours());
  const mm = pad(date.getMinutes());

  return `${y}.${m}.${d} (${weekday}) ${hh}:${mm}`;
}
