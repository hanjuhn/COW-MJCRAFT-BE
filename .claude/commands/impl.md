# /impl — 계획 기반 구현

## 역할
`.ai-workspace/plan.md`에 승인된 계획대로만 구현한다.
계획에 없는 변경, 리팩토링, 정리는 하지 않는다.

## 사전 조건
- `.ai-workspace/plan.md` 파일이 존재해야 한다
- 사용자가 /plan 결과를 승인한 상태여야 한다

## 실행 순서

### 0. 사전 조건 확인
`.ai-workspace/plan.md` 파일 존재 여부를 확인한다.

파일이 없으면 즉시 다음 메시지를 출력하고 종료한다:
```
.ai-workspace/plan.md 파일이 없습니다.
/plan을 먼저 실행하세요.
```

### 1. 계획 확인
- `.ai-workspace/plan.md` 읽기
- AGENTS.md 읽어 컨벤션 재확인

### 2. 구현
계획의 작업 순서대로 파일을 생성/수정한다.

**파일 생성/수정 시 준수 사항**

_DTO_
- 신규 Request DTO: `record` 사용, 이름은 `XxxRequestDto`
- 신규 Response DTO: `record` 사용, 이름은 `XxxResponseDto`
- `from(Entity)` 메서드 불필요 — 서비스에서 직접 생성
- Validation 어노테이션: 어노테이션별 반드시 별도 줄

_예외_
- `IllegalArgumentException`, `RuntimeException` 직접 throw 금지
- 새 예외 필요 시: `XxxErrorType` enum (`ErrorCode` 구현) → `XxxException extends DomainException` 생성
- `throw new XxxException(XxxErrorType.XXX)` 패턴 사용

_공통 응답_
- 컨트롤러 반환: `ApiResponse.of(SuccessType.XXX, data)` 또는 `ApiResponse.of(SuccessType.XXX)`
- Swagger import 충돌: `com.example.cowmjucraft.global.response.ApiResponse`는 import, `io.swagger.v3.oas.annotations.responses.ApiResponse`는 FQN 사용

_Entity_
- setter 추가 금지, 상태 변경은 의미 있는 메서드로
- `BaseTimeEntity` 상속 필수
- `@Enumerated(EnumType.STRING)` 사용

_Controller_
- Swagger 문서는 `*ControllerDocs` 인터페이스로 분리, Controller가 `implements`
- admin API: `/api/admin/...`, 사용자 API: `/api/...`

_S3_
- S3 직접 조작 금지 — `S3PresignFacade` 또는 `S3ObjectService` 경유

- 새 파일 생성 시: 파일 경로와 생성 이유를 한 줄로 사용자에게 알림

### 3. 컴파일 확인
구현 완료 후 반드시 실행:
```bash
./gradlew compileJava
```

**컴파일 실패 시**
- 즉시 구현 중단
- 오류 메시지 전문을 사용자에게 보고
- 수정 방향 제안 후 승인 대기

### 4. 결과 보고
- 생성/수정한 파일 목록
- 컴파일 결과
- 계획 대비 변경된 사항이 있으면 명시

## 주의사항
- 자동 커밋/푸시 절대 금지
- 계획에 없는 파일 수정 금지
- 계획에 없는 리팩토링, 코드 정리 금지
- 보안 관련 파일(SecurityConfig, JwtAuthenticationFilter, JwtTokenProvider) 수정 포함 시 구현 전 사용자에게 재확인
