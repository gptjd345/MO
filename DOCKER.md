# TaskFlow - Docker 배포 가이드

## 사전 준비

- Docker 및 Docker Compose 설치 필요
- Docker Desktop (Windows/Mac) 또는 Docker Engine (Linux)

## 빠른 시작

```bash
# 1. 앱 빌드 및 실행 (첫 실행 시 빌드에 몇 분 소요)
docker compose up -d

# 2. 브라우저에서 접속
# http://localhost:5000
```

## 프로덕션 환경 설정

프로젝트 루트에 `.env` 파일을 생성하여 보안 설정을 관리하세요:

```bash
# .env
POSTGRES_DB=taskflow
POSTGRES_USER=taskflow
POSTGRES_PASSWORD=여기에_안전한_비밀번호_입력
JWT_SECRET=여기에_256비트_이상의_랜덤_문자열_입력
APP_PORT=5000
```

`.env` 파일은 반드시 `.gitignore`에 추가하여 Git에 포함되지 않도록 하세요.

## 주요 명령어

```bash
# 컨테이너 상태 확인
docker compose ps

# 로그 확인
docker compose logs -f        # 전체 로그
docker compose logs -f app    # 앱 로그만
docker compose logs -f postgres  # DB 로그만

# 앱 중지
docker compose down

# 앱 중지 + 데이터 삭제 (DB 초기화)
docker compose down -v

# 코드 변경 후 재빌드
docker compose up -d --build
```

## 서비스 구성

| 서비스 | 내부 포트 | 설명 |
|--------|-----------|------|
| app | 5000 | Express + Spring Boot (프론트엔드 + API) |
| postgres | 5432 | PostgreSQL 16 데이터베이스 (내부 전용) |
| redis | 6379 | Redis 7 (JWT 토큰 관리, 내부 전용) |

PostgreSQL과 Redis는 Docker 네트워크 내부에서만 접근 가능하며, 외부에 노출되지 않습니다.

## 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| POSTGRES_DB | taskflow | PostgreSQL 데이터베이스 이름 |
| POSTGRES_USER | taskflow | PostgreSQL 사용자 |
| POSTGRES_PASSWORD | taskflow123 | PostgreSQL 비밀번호 (반드시 변경) |
| JWT_SECRET | (기본값) | JWT 토큰 서명 키 (반드시 변경) |
| APP_PORT | 5000 | 외부 접근 포트 |

## 아키텍처

```
[브라우저] → :5000 → [Express/Node.js]
                        ├── 정적 파일 서빙 (React 빌드)
                        └── /api/* → [Spring Boot :8080]
                                       ├── [PostgreSQL :5432]
                                       └── [Redis :6379]
```

Docker Compose에서 `depends_on` + `healthcheck`로 PostgreSQL과 Redis가 준비된 후에 앱이 시작됩니다.

## 트러블슈팅

**Spring Boot가 시작되지 않는 경우:**
```bash
docker compose logs -f app
```
로그에서 `[spring-boot]` 접두사가 붙은 메시지를 확인하세요.

**DB 연결 오류:**
```bash
docker compose exec postgres pg_isready -U taskflow
```

**모든 데이터 초기화 후 재시작:**
```bash
docker compose down -v && docker compose up -d --build
```
