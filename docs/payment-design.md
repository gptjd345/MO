# 결제 처리 설계

## 개요

외부 PG(Payment Gateway)와의 통신은 본질적으로 불안정합니다.
클라이언트가 응답을 받지 못하면 재시도하고, 중복 결제가 발생하면 안 됩니다.
이 문제를 **멱등성**으로 해결했습니다.

---

## 기술적 고민 1 — 중복 결제 방지 (멱등성)

### 문제

클라이언트가 결제 요청을 보냈는데 응답이 오지 않으면 재시도합니다.
서버가 두 요청을 모두 처리하면 중복 결제가 발생합니다.

### 해결: 클라이언트 생성 idempotencyKey

클라이언트가 결제 요청 시 UUID로 생성한 `idempotencyKey`를 함께 전송합니다.
서버는 `(userId, idempotencyKey)` 복합 유니크 인덱스로 중복을 차단합니다.

```
첫 번째 요청: idempotencyKey = "uuid-1234"
  → DB에 없음 → 결제 진행 → payments 테이블에 저장

두 번째 요청 (재시도): idempotencyKey = "uuid-1234"
  → DB에 있음 + status = SUCCESS → 캐시된 결과 반환 (PG 재호출 없음)
```

단말 상태(SUCCESS/FAIL)에 도달한 결제는 동일 키로 재요청이 와도 기존 결과를 그대로 반환합니다.

### PG에도 scoped key 전달

PG 자체 중복 방지를 위해 `{userId}:{idempotencyKey}` 형식으로 키를 조합해서 전달합니다.
userId를 포함시키는 이유는 서로 다른 사용자가 우연히 같은 idempotencyKey를 만들어도 PG 레벨에서 충돌하지 않도록 하기 위함입니다.

---

## 결제 상태 흐름

```
INIT → PENDING → SUCCESS
               → FAIL (사유: INSUFFICIENT_FUNDS / PG_TIMEOUT / PG_SYSTEM_ERROR 등)
```

`PENDING` 상태를 별도로 두는 이유는 처리 중 서버가 재시작되면 미완 결제를 식별해 복구할 수 있도록 하기 위함입니다.

PG 호출 실패(타임아웃, 시스템 오류) 시 자동 재시도는 중복 결제 위험이 있어 적용하지 않았습니다.
실패 사유를 응답에 포함해 고객이 직접 재시도하도록 합니다.

---

## PgClient 인터페이스 추상화

실제 PG사 연동 전에 비즈니스 로직을 검증할 수 있어야 했습니다.
`PgClient` 인터페이스를 정의하고 `FakePgClient`로 다양한 시나리오를 시뮬레이션했습니다.

```
PgClient (interface)
  └── FakePgClient
        ├── 70% 성공
        ├── 10% 타임아웃
        ├── 15% 잔액 부족
        └── 5% 시스템 오류
```

PG 구현체를 교체해도 서비스 레이어 코드를 수정할 필요가 없습니다.

---

## 정리

| 문제 | 해결 방법 |
|------|-----------|
| 중복 결제 | 클라이언트 생성 idempotencyKey + DB 유니크 인덱스 |
| PG 오류 | 즉시 FAIL 처리 후 고객에게 재시도 위임 |
| PG 교체 가능성 | PgClient 인터페이스 추상화 |