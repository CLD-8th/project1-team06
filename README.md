# 6팀 Project 1

Spring Boot 4.1 (JDK 21) + MySQL 8.4 + Redis 7.4, Docker Compose 구성.

## 실행 방법

```bash
git clone <저장소 URL>
cd team6-project

cp .env.example .env
vi .env                      # 각 값 입력

docker login
docker compose up -d --build
docker compose ps            # app, db, cache 모두 running (db, cache는 healthy)
```

## 확인 URL

- 헬스 체크: http://localhost:8080/actuator/health (`{"status":"UP"}`)

## 인증 (JWT)

| 기능 | Method | URL | 인증 |
|---|---|---|---|
| 회원가입 | POST | /auth/signup | 불필요 |
| 로그인 (토큰 발급) | POST | /auth/login | 불필요 |
| 내 정보 | GET | /users/me | 필요 |

```bash
curl -s -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"password1234","nickname":"user"}' \
  http://localhost:8080/auth/signup

curl -s -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"password1234"}' \
  http://localhost:8080/auth/login          # accessToken 확인

curl -s -H 'Authorization: Bearer <accessToken>' http://localhost:8080/users/me
```

- 토큰 유효 시간은 1시간이고, `.env`의 `JWT_SECRET`(32바이트 이상)으로 서명함
- 컨트롤러에서 로그인 사용자 ID는 `@AuthenticationPrincipal Jwt jwt`로 받아 `Long.valueOf(jwt.getSubject())`로 꺼냄

## 운동 기록

| 기능 | Method | URL | 인증 |
|---|---|---|---|
| 기록 등록 | POST | /workouts | 필요 |
| 기록 상세 | GET | /workouts/{id} | 불필요 |
| 특정 사용자 기록 목록 | GET | /users/{id}/workouts?page=0 | 불필요 |
| 기록 삭제 | DELETE | /workouts/{id} | 필요 (작성자만) |
| 인증사진 첨부 (선택) | POST | /workouts/{id}/photos | 필요 (작성자만) |

```bash
TOKEN=<accessToken>

# 기록 등록
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"CARDIO","durationMin":40,"distanceKm":5.2,"memo":"한강 러닝","workoutDate":"2026-09-28"}' \
  http://localhost:8080/workouts

# 기록 상세 / 목록 (인증 불필요)
curl -s http://localhost:8080/workouts/1
curl -s "http://localhost:8080/users/1/workouts?page=0"

# 인증사진 첨부 (multipart part 이름: photo, 최대 5MB, JPG/PNG/WEBP)
curl -s -H "Authorization: Bearer $TOKEN" \
  -F "photo=@/path/to/photo.jpg;type=image/jpeg" \
  http://localhost:8080/workouts/1/photos
# → {"photoUrl":"/photos/<storedKey>","storedKey":"..."} , 이후 GET http://localhost:8080/photos/<storedKey>로 조회

# 기록 삭제 (작성자만, 사진도 함께 삭제됨)
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/workouts/1
```

- `type`은 `CARDIO`(유산소) / `STRENGTH`(무산소)이고, `distanceKm`은 유산소일 때만 선택 입력함
- `durationMin`(1~300)이 주간 통계·주간 랭킹 점수 계산의 공통 기준값임
- 기록 등록 시 `rankingService.addScore(...)`, 삭제 시 `rankingService.subtractScore(...)`를 호출해 주간 랭킹에 반영함 (`subtractScore`는 A 파트에서 추가)
- `WorkoutRepository.sumMinutesByDate(userId, start, end)`가 사용자 주간 날짜별 운동시간 합계를 반환함. 주간 통계에서 이 쿼리를 재사용하면 됨
- 인증 없이 허용하는 경로는 `config/SecurityConfig.java`에서 관리함

## 댓글

| 기능 | Method | URL | 인증 |
|---|---|---|---|
| 댓글 등록 | POST | /workouts/{id}/comments | 필요 |
| 댓글 목록 | GET | /workouts/{id}/comments | 불필요 |
| 댓글 삭제 | DELETE | /comments/{id} | 필요 (작성자만) |

```bash
TOKEN=<accessToken>

# 댓글 등록
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"content":"오늘도 화이팅!"}' \
  http://localhost:8080/workouts/1/comments

# 댓글 목록 (인증 불필요, 오래된순)
curl -s http://localhost:8080/workouts/1/comments

# 댓글 삭제 (작성자만)
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/comments/1
```

- 내용은 1~300자, 없는 기록에 달면 404
- `GET /workouts/{id}/comments`는 `/workouts/**` GET 허용 규칙에 이미 걸려서 SecurityConfig를 따로 안 고쳤음

## 팔로우 + 피드 (B)

| 기능 | Method | URL | 인증 | 응답 |
|---|---|---|---|---|
| 팔로우 | POST | /users/{id}/follow | 필요 | 204, 자기 자신 400, 없는 사용자 404, 이미 팔로우 409 |
| 언팔로우 | DELETE | /users/{id}/follow | 필요 | 204 (팔로우하지 않은 상대여도 204) |
| 피드 | GET | /feed?page=0 | 필요 | 200, 팔로우한 사람들 기록 최신순 20개 (`/workouts/{id}` 응답과 같은 형태) |
| 사람 찾기 · 검색 | GET | /users?q=닉네임&page=0 | 필요 | 200, 나를 뺀 사용자 최신 가입순 20명 `[{id, nickname, following}]` (q 생략 시 전체, 닉네임 부분 일치) |
| 프로필 | GET | /users/{id}/profile | 필요 | 200 `{id, nickname, workoutCount, followerCount, followeeCount, following, me}`, 없는 사용자 404 |

```bash
curl -s -X POST -H 'Authorization: Bearer <accessToken>' http://localhost:8080/users/2/follow -w '%{http_code}\n'
curl -s -H 'Authorization: Bearer <accessToken>' 'http://localhost:8080/feed?page=0'
```

- 화면 (오운공): http://localhost:8080/workouts.html — 관심 피드 · 사람 검색 · 프로필 · 팔로우 / 팔로우 취소 · 운동 기록 등록(사진) · 상세 · 삭제 · 주간 랭킹
- `follows` 테이블의 `(follower_id, followee_id)` 유일 제약으로 중복 팔로우를 막음
- 피드는 작성자를 함께 조회(join fetch)해 20건이어도 쿼리 1번으로 가져옴
- `workout/Workout`, `workout/WorkoutResponse` 는 B 파트가 먼저 최소 형태로 추가했고, A 파트(운동 기록)가 사진 첨부·랭킹 연동을 붙여 확장함

## 마이페이지

| 기능 | Method | URL | 인증 | 응답 |
|---|---|---|---|---|
| 닉네임 수정 | PATCH | /users/me | 필요 | 200 `{id, email, nickname, photoUrl, joinedAt}`, 1~30자 아니면 400 |
| 프로필 사진 올리기 · 바꾸기 | POST | /users/me/photo | 필요 | multipart `photo`, JPG/PNG/WEBP 5MB, 200 (위와 같은 형태) |
| 프로필 사진 삭제 | DELETE | /users/me/photo | 필요 | 204 |
| 운동 요약 | GET | /users/me/summary | 필요 | 200 `{totalCount, totalMinutes, weekCount, weekMinutes, weekRank, streakDays, byType:[{type, count, minutes}]}` |
| 내 주간 통계 (11번) | GET | /users/me/stats/weekly?date= | 필요 | 200 `{week, start, end, workoutCount, totalMinutes, byType, days:[{date, minutes}] (월~일 7칸)}` |
| 팔로워 · 팔로잉 목록 | GET | /users/{id}/followers, /users/{id}/followees | 필요 | 200 `[{id, nickname, photoUrl, following}]` 20명씩 |
| 회원 탈퇴 | DELETE | /users/me | 필요 | 본문 `{password}`, 204, 비밀번호가 틀리면 400 |

- 프로필 사진은 인증사진과 같은 `app.upload-dir`에 `profile_...` 키로 저장하고 `/photos/{key}`로 조회함. `users.profile_image_key` 컬럼 추가
- 사진을 바꾸면 DB 커밋 뒤에 예전 파일을 지움 (저장 실패 시 예전 사진 유지)
- `weekRank`는 주간 랭킹 Sorted Set의 순위(ZREVRANK + 1). 랭킹에 없거나 Redis 장애면 null이고 나머지 요약은 정상 응답
- `streakDays`는 오늘(오늘 기록이 없으면 어제)부터 하루도 빠짐없이 기록한 날 수
- 6번 `/users/{id}/workouts`에 `type=CARDIO|STRENGTH` 필터 추가 (생략하면 기존과 같음)
- 회원 탈퇴는 한 트랜잭션에서 내 기록(남이 단 댓글 포함) · 인증사진 · 내가 쓴 댓글 · 팔로우 관계 · 계정을 삭제하고, 커밋 뒤에 사진 파일과 주간 랭킹(Redis) 점수를 정리함. 같은 이메일로 다시 가입 가능
- 화면: http://localhost:8080/workouts.html#/me

## 주간 랭킹 (Redis)

| 기능 | Method | URL | 인증 |
|---|---|---|---|
| 주간 운동 시간 TOP 10 | GET | /rankings/weekly?date=2026-09-28 (date 생략 시 이번 주) | 불필요 |

- 키: `ranking:weekly:{yyyy}-W{ww}` (ISO 주차, 월요일 시작, Asia/Seoul), Sorted Set, 5주 뒤 자동 만료
- 운동 기록을 저장한 뒤 아래 한 줄을 호출하면 랭킹에 반영됨

```java
rankingService.addScore(userId, durationMin, workoutDate);
```

```bash
# Redis에서 이번 주 랭킹 확인
docker compose exec cache sh -c 'redis-cli -a "$REDIS_PASSWORD" ZREVRANGE ranking:weekly:2026-W40 0 9 WITHSCORES'
```

## 테스트

```bash
./gradlew test --tests 'com.team6.app.ranking.*'
```

Windows 사용자 이름에 한글이 있으면 Mockito가 임시 폴더를 쓰지 못해 실패할 수 있음. 이때는 영문 경로를 임시 폴더로 지정해 실행함.

```bash
TMP='C:\Users\Public\tmp' TEMP='C:\Users\Public\tmp' ./gradlew test --tests 'com.team6.app.ranking.*'
```

## 재현 테스트

```bash
docker compose down -v
docker compose up -d --build
curl -s http://localhost:8080/actuator/health
```

## 디렉터리 구조

```
team6-project/
├── compose.yaml            # 서비스 정의 (app, db, cache)
├── .env                    # 실제 값 (Git 추적 제외)
├── .env.example            # 키 목록만 (커밋)
├── .dockerignore
├── Dockerfile
├── build.gradle
├── gradlew
└── src/
```
