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

## 팔로우 + 피드 (B)

| 기능 | Method | URL | 인증 | 응답 |
|---|---|---|---|---|
| 팔로우 | POST | /users/{id}/follow | 필요 | 204, 자기 자신 400, 없는 사용자 404, 이미 팔로우 409 |
| 언팔로우 | DELETE | /users/{id}/follow | 필요 | 204 (팔로우하지 않은 상대여도 204) |
| 피드 | GET | /feed?page=0 | 필요 | 200, 팔로우한 사람들 기록 최신순 20개 (`/workouts/{id}` 응답과 같은 형태) |

```bash
curl -s -X POST -H 'Authorization: Bearer <accessToken>' http://localhost:8080/users/2/follow -w '%{http_code}\n'
curl -s -H 'Authorization: Bearer <accessToken>' 'http://localhost:8080/feed?page=0'
```

- `follows` 테이블의 `(follower_id, following_id)` 유일 제약으로 중복 팔로우를 막음
- 피드는 작성자를 함께 조회(join fetch)해 20건이어도 쿼리 1번으로 가져옴
- `workout/Workout`, `workout/WorkoutResponse` 는 피드에 필요한 최소 형태로 먼저 추가함 (A 파트에서 확장)

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
