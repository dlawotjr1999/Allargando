// 모집글 등록·수정 폼의 입력 검증. 순수 함수만 모음(외부 의존 없음).
// 서버(PostCreateRequestDTO)의 제약을 미리 걸러 사용자가 요청을 보내기 전에 어디가 틀렸는지 알 수 있게 한다.
// 서버가 최종 검증을 하므로 여기서 통과해도 서버가 거절할 수 있다(예: 서버 시각 기준 @Future).

// 서버 @Size 상한 — 입력칸의 maxLength와 같은 값을 쓴다
export const POST_TITLE_MAX = 255;
export const POST_LOCATION_MAX = 255;
export const POST_TIMETABLE_MAX = 255;
export const POST_DESCRIPTION_MAX = 2000;
// 서버 @Min/@Max — 악기별 모집 인원
export const POST_PEOPLE_MIN = 1;
export const POST_PEOPLE_MAX = 100;

const DATE_RE = /^(\d{4})-(\d{2})-(\d{2})$/;
const TIME_RE = /^(\d{2}):(\d{2})$/;

export interface PostFormValues {
  category: string;
  title: string;
  eventDate: string; // "YYYY-MM-DD"
  eventTime: string; // "HH:mm"
  location: string;
  region: string;
  timetable: string;
  instruments: { instrument: string; people: string }[];
}

// 날짜·시간 문자열을 실제 존재하는 일시로 해석한다. 형식이 틀리거나 없는 날짜(2026-02-30)·시각(25:00)이면 null.
// new Date(y, m-1, d)는 없는 날짜를 다음 달로 넘겨 버리므로 되읽어 같은지 확인한다
export function parseEventDateTime(eventDate: string, eventTime: string): Date | null {
  const d = DATE_RE.exec(eventDate);
  const t = TIME_RE.exec(eventTime);
  if (!d || !t) return null;
  const [year, month, day] = [Number(d[1]), Number(d[2]), Number(d[3])];
  const [hour, minute] = [Number(t[1]), Number(t[2])];
  const date = new Date(year, month - 1, day, hour, minute);
  const same =
    date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day &&
    date.getHours() === hour &&
    date.getMinutes() === minute;
  return same ? date : null;
}

// 첫 번째로 틀린 항목의 안내 문구를 돌려준다(모두 맞으면 null). 폼 위에서 아래 순서로 검사한다.
// now는 테스트에서 시각을 고정하기 위한 인자
export function validatePostForm(values: PostFormValues, now: Date = new Date()): string | null {
  if (!values.category) return "카테고리를 선택해 주세요.";
  if (!values.title.trim()) return "제목을 입력해 주세요.";
  if (values.title.length > POST_TITLE_MAX) return `제목은 ${POST_TITLE_MAX}자 이내로 입력해 주세요.`;

  if (!DATE_RE.test(values.eventDate)) return "공연 날짜를 2026-08-01 형식으로 입력해 주세요.";
  if (!TIME_RE.test(values.eventTime)) return "공연 시간을 14:00 형식(24시간)으로 입력해 주세요.";
  const eventAt = parseEventDateTime(values.eventDate, values.eventTime);
  if (!eventAt) return "존재하지 않는 날짜 또는 시간이에요. 다시 확인해 주세요.";
  if (eventAt <= now) return "공연 일시는 현재 이후여야 해요.";

  if (!values.location.trim()) return "장소를 입력해 주세요.";
  if (values.location.length > POST_LOCATION_MAX) return `장소는 ${POST_LOCATION_MAX}자 이내로 입력해 주세요.`;
  if (!values.region) return "지역을 선택해 주세요.";
  if (!values.timetable.trim()) return "시간표를 입력해 주세요.";
  if (values.timetable.length > POST_TIMETABLE_MAX) {
    return `시간표는 ${POST_TIMETABLE_MAX}자 이내로 입력해 주세요.`;
  }

  // 악기·인원이 모두 빈 행은 아직 안 채운 줄로 보고 무시하고, 하나만 채운 행은 오류로 안내한다
  const filled = values.instruments.filter((it) => it.instrument || it.people);
  if (filled.length === 0) return "모집 악기를 1개 이상 입력해 주세요.";
  for (const it of filled) {
    if (!it.instrument) return "모집 인원을 입력한 악기를 선택해 주세요.";
    const people = Number(it.people);
    if (!it.people || !Number.isInteger(people) || people < POST_PEOPLE_MIN || people > POST_PEOPLE_MAX) {
      return `${it.instrument}의 모집 인원은 ${POST_PEOPLE_MIN}~${POST_PEOPLE_MAX}명으로 입력해 주세요.`;
    }
  }
  if (new Set(filled.map((it) => it.instrument)).size !== filled.length) {
    return "같은 악기를 두 번 넣을 수 없어요.";
  }

  return null;
}
