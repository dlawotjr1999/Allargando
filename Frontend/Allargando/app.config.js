// app.json 위에 얹는 동적 설정. google-services.json은 gitignore 대상이라 EAS 클라우드 빌드에는 올라가지 않으므로,
// EAS 파일 변수 GOOGLE_SERVICES_JSON(빌드 서버에서 파일 경로로 풀림)이 있으면 그것을, 없으면(로컬) app.json의 경로를 쓴다.
module.exports = ({ config }) => ({
  ...config,
  android: {
    ...config.android,
    googleServicesFile: process.env.GOOGLE_SERVICES_JSON ?? config.android?.googleServicesFile,
  },
});
