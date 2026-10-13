package com.wangnu.allargando.global.version;

import com.wangnu.allargando.global.exception.UpgradeRequiredException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/*
 * 강제 업데이트 검사 — 앱이 요청마다 보내는 X-App-Version이 설정한 최소 버전(app.min-version)보다 낮으면 426으로 거절한다.
 *   - 최소 버전이 비어 있으면 검사하지 않는다(기본값). 운영에서 APP_MIN_VERSION을 올리는 것으로 켠다.
 *   - 헤더가 없는 요청은 통과한다: 버전을 보내기 전의 빌드, Swagger·curl 같은 앱 밖 호출을 막지 않기 위해서다.
 *   - 읽을 수 없는 버전 값도 통과한다(AppVersions).
 * 앱이 서버보다 먼저 배포된 옛 빌드에 계약을 깨는 변경을 할 때, 옛 빌드를 안내 화면으로 보내는 유일한 수단이다.
 */
public class AppVersionInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-App-Version";
    static final String MESSAGE = "앱을 최신 버전으로 업데이트해 주세요";

    private final String minimumVersion;

    public AppVersionInterceptor(String minimumVersion) {
        this.minimumVersion = minimumVersion == null ? "" : minimumVersion.trim();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (minimumVersion.isEmpty()) {
            return true;
        }
        String version = request.getHeader(HEADER);
        if (version != null && AppVersions.isBelow(version, minimumVersion)) {
            throw new UpgradeRequiredException(MESSAGE);
        }
        return true;
    }
}
