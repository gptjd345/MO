# TaskFlow

할 일 관리 웹 애플리케이션. 무료/Pro 플랜을 지원하며 결제 연동이 포함된 풀스택 SaaS 구조.

## 기술 스택

| 레이어 | 기술 |
|--------|------|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, TanStack Query, Framer Motion |
| Backend | Spring Boot 3.2, Java 17, Spring Security (JWT), JPA |
| Database | PostgreSQL 16 |
| Cache / Session | Redis 7 |
| Node.js layer | Express 5 (프론트 서빙 + Spring Boot 프록시) |

## 프로젝트 구조

```
.
├── frontend/
│   ├── src/
│   │   ├── pages/        # auth-page, dashboard-page
│   │   ├── components/   # layout-shell, UI 컴포넌트
│   │   └── hooks/        # use-auth, use-todos
│   └── server/           # Express 서버 (Vite dev + /api 프록시)
└── backend/
    └── src/main/java/com/todo/
        ├── controller/   # Auth, Todo, Payment REST API
        ├── service/
        ├── entity/       # User, Todo, Payment
        └── security/     # JWT 인증
```

## 실행 방법

### 개발 환경 (Docker)

```bash
docker compose -f docker-compose.dev.yml up
```

| 서비스 | 주소 |
|--------|------|
| 앱 (Node dev server) | http://localhost:3001 |
| Spring Boot API | http://localhost:8080 |
| PostgreSQL | localhost:5433 |
| Redis | localhost:6380 |

### 로컬 개발 (Node만)

Spring Boot가 별도로 실행 중인 상태에서:

```bash
npm install
npm run dev       # http://localhost:5000
```

### 프로덕션 빌드

```bash
# Docker
docker compose up -d

# 직접 빌드
npm run build
npm start
```

## 요청 흐름

```
Browser → Express (5000)
              ├── 정적 파일 (React 빌드)
              └── /api/* → Spring Boot (8080)
                               ├── PostgreSQL
                               └── Redis
```

## 환경 변수

### Node / Express

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `PORT` | `5000` | Express 서버 포트 |
| `SPRING_BOOT_URL` | `http://127.0.0.1:8080` | Spring Boot 주소 |
| `REDIS_HOST` | `127.0.0.1` | Redis 호스트 |
| `REDIS_PORT` | `6379` | Redis 포트 |

### Spring Boot

| 변수 | 설명 |
|------|------|
| `DATABASE_URL` | PostgreSQL 연결 문자열 |
| `REDIS_HOST` | Redis 호스트 |
| `REDIS_PORT` | Redis 포트 |
| `JWT_SECRET` | JWT 서명 키 (256비트 이상) |

### PostgreSQL

| 변수 | 기본값 |
|------|--------|
| `POSTGRES_DB` | `taskflow` |
| `POSTGRES_USER` | `taskflow` |
| `POSTGRES_PASSWORD` | `taskflow123` |

> **프로덕션에서는 `POSTGRES_PASSWORD`와 `JWT_SECRET`을 반드시 변경하세요.**

## 주요 기능

- 회원가입 / 로그인 (JWT 인증)
- 할 일 CRUD (제목, 내용, 우선순위, 시작일/마감일)
- 정렬 (최신순, 이름순, 마감일순, 우선순위순)
- 검색 필터
- 무료 플랜: 최대 10개, Pro 플랜: 무제한
- 결제 처리 (PG 연동, 실패 시뮬레이션 포함)
