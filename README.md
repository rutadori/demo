# アツイエ — 일본 부동산 매물 모아보기

Spring Boot·Thymeleaf·MyBatis 기반 MVC 프로젝트입니다. 기본 실행 DB는 팀 공용 Supabase PostgreSQL이며, 사진 파일 대신 외부 이미지 URL을 DB에 저장합니다.

## 팀원 실행 방법

1. JDK 21을 설치하고 IntelliJ에서 Gradle 프로젝트를 엽니다.
2. `config/supabase-local.properties.example`을 같은 폴더의 `supabase-local.properties`로 복사합니다.
3. `YOUR_RUNTIME_DB_PASSWORD`를 팀에서 별도로 전달받은 앱 전용 DB 계정 비밀번호로 바꿉니다. Supabase 관리자 비밀번호나 API 키는 필요하지 않습니다.
4. 실행 작업 디렉터리를 프로젝트 루트로 지정하고 앱을 실행합니다. 기본 프로필은 `supabase`입니다. IDE나 환경변수에 예전 `mysql` 프로필이 있다면 해제하거나 `supabase`로 변경합니다.
5. `http://localhost:8085/`에 접속합니다. 포트가 사용 중이면 기존 실행을 정상 종료한 뒤 다시 실행합니다.

PowerShell에서는 `JAVA_HOME`에 JDK 21 경로를 지정한 뒤 프로젝트 루트에서 다음을 실행할 수 있습니다.

```powershell
.\gradlew.bat bootRun
```

접속 정보는 예시 파일에 있습니다. Session pooler의 5432 포트와 `house_app` 스키마를 사용하며, `config/supabase-ca.crt`로 서버 인증서와 호스트명을 검증합니다. 인증서 경로 오류가 나면 실행 작업 디렉터리를 확인합니다. 별도 인증서 경로는 `SUPABASE_SSL_ROOT_CERT` 환경변수로 지정할 수 있습니다.

## 비밀번호와 데이터 공유

`config/supabase-local.properties`, `config/mysql-local.properties`, `.env`는 로컬 비밀 설정이며 Git에서 제외합니다. 공개 저장소나 프로젝트 압축 파일에 포함하지 마세요. `.gitignore`는 압축 프로그램에는 적용되지 않습니다. 공개 CA 인증서와 `.example` 파일은 공유합니다.

Supabase에는 출처 4건·매물 583건을 이미 이전했습니다. 팀원은 같은 DB에 연결하면 되며 SQL을 다시 실행할 필요가 없습니다. 이후 한 사람이 갱신한 데이터는 다른 팀원에게도 반영됩니다. 전체 크롤링은 팀 내에서 조율해서 실행하세요. 일반 시작 시 자동 수집은 비활성화되어 있습니다.

`database/001_supabase_schema.sql`과 `002_supabase_snapshot.sql`은 다른 빈 PostgreSQL DB에 처음 구성할 때 참고하는 초기 스키마·데이터입니다. 현재 팀 공용 DB에 재실행하지 마세요. 데이터 파일은 반복 실행용 upsert가 아닙니다. 스키마 파일의 앱 역할은 NOLOGIN이며 새 DB에서 로그인 비밀번호를 별도로 설정해야 합니다.

## 검증 및 이전 MySQL

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
```

테스트는 실제 선택 DB에 연결하므로 Supabase 설정과 네트워크가 필요합니다. Mapper 테스트 데이터는 롤백되며 시퀀스 번호에는 빈 번호가 생길 수 있습니다. 외부 사이트 크롤링 요청은 테스트에서 실행하지 않습니다. 이전 시점에 PostgreSQL에서 기존 테스트 39개 통과와 전체 필드 데이터 일치를 확인했습니다.

이전 MySQL로 실행할 때는 `SPRING_PROFILES_ACTIVE=mysql`을 지정하고 프로젝트 루트의 `config/mysql-local.properties`에 `MYSQL_DB_URL`, `MYSQL_DB_USERNAME`, `MYSQL_DB_PASSWORD`를 설정합니다. MySQL과 Supabase 사이에 자동 동기화는 없습니다.

현재 로그인은 개발용 임시 인증이며 실제 회원가입·소셜 로그인·통근 추천 기능은 후속 작업입니다. 기존 Q&A는 고정 예시 화면입니다.
