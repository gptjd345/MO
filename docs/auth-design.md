# 인증 시스템 설계

## 개요

JWT 기반 인증에서 가장 흔한 문제는 **"발급된 토큰을 어떻게 무효화하는가"** 입니다.
JWT는 Stateless이기 때문에 서버가 토큰을 취소할 방법이 없습니다. 이 문제를 두 가지 목적에 맞게 전략을 분리하여 해결했습니다.

---

## 왜 Access Token + Refresh Token을 분리했는가

Access Token의 만료 시간을 길게 설정하면 탈취 시 피해가 크고, 짧게 설정하면 로그인을 자주 해야 합니다.
만료 시간을 **15분**으로 짧게 가져가되, Refresh Token(7일)으로 자동 재발급하여 사용자 경험을 유지했습니다.

| 토큰 | 만료 | 저장 위치 | 목적 |
|------|------|-----------|------|
| Access Token | 15분 | Authorization 헤더 | API 인가 |
| Refresh Token | 7일 | HTTP-only Cookie | 액세스 토큰 재발급 |

Refresh Token을 **HTTP-only Cookie**에 저장한 이유는 JavaScript에서 접근할 수 없으므로 XSS 공격으로 탈취되지 않기 때문입니다.
Access Token을 **헤더**로 주고받는 이유는 Cookie에 담으면 CSRF 공격에 노출될 수 있기 때문입니다.

---

## 기술적 고민 1 — 전체 디바이스 일괄 로그아웃

### 문제

비밀번호 변경이나 보안 이벤트 발생 시 모든 디바이스의 세션을 즉시 만료시켜야 합니다.
Refresh Token을 DB에서 전부 revoke하면 되지만, **이미 발급된 Access Token(15분)은 여전히 유효합니다.**

### 해결: Token Versioning

`users` 테이블에 `token_version` 컬럼을 두고, Access Token 페이로드에 발급 시점의 버전을 `tv` claim으로 심었습니다.

```
보안 이벤트 발생
  → users.token_version += 1
  → Redis 캐시 evict
  → 기존 토큰의 tv != 현재 버전 → JwtAuthFilter에서 401 반환
```

모든 디바이스의 기존 토큰이 다음 요청 시 일괄 차단됩니다. Access Token 만료를 기다릴 필요 없이 즉시 무효화됩니다.

### Redis 캐시를 쓰는 이유

매 API 요청마다 DB에서 token_version을 조회하면 불필요한 DB 부하가 생깁니다.
Redis에 `tv:{userId}` 키로 캐싱하고, 버전 변경 시 evict합니다. Redis miss 시 DB에서 fallback 조회합니다.

```
요청 → Redis 조회(hit) → 버전 비교 → 통과/차단
요청 → Redis 조회(miss) → DB 조회 → Redis 갱신 → 버전 비교
```

---

## 기술적 고민 2 — 단일 디바이스 로그아웃

### 문제

"이 기기에서만 로그아웃"은 해당 Refresh Token만 무효화하면 됩니다.
하지만 Refresh Token도 JWT이므로 탈취 후 재사용을 막아야 합니다.

### 해결: JTI + Refresh Token Rotation

Refresh Token 발급 시 UUID 기반의 **JTI(JWT ID)** 를 생성하여 `refresh_tokens` 테이블에 저장합니다.

- 로그아웃: 해당 JTI의 `revoked_at` 기록 → 이후 재발급 요청 차단
- 재발급(Rotation): 기존 JTI를 revoke하고 새 토큰 발급 — 탈취된 토큰으로 재사용 불가

```
refresh_tokens
  ├── jti (UUID, UNIQUE)
  ├── expires_at
  └── revoked_at (NULL이면 유효)
```

Rotation을 적용하면 탈취자가 Refresh Token을 사용하는 순간 정상 사용자의 재발급이 실패하므로 이상 감지가 가능합니다.

---

## 정리

| 무효화 목적 | 전략 | 매커니즘 |
|------------|------|----------|
| 전체 디바이스 | Token Versioning | token_version 증가 + Redis 캐시 evict |
| 단일 디바이스 | JTI Revocation + Rotation | refresh_tokens.revoked_at 기록 |

두 전략을 목적에 따라 분리함으로써 보안성과 UX를 모두 충족했습니다.
