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
- 인증 없이 허용하는 경로는 `config/SecurityConfig.java`에서 관리함

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
