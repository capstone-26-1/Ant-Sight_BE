# Ant-Sight Backend

Spring Boot 기반 주식 정보 서비스 백엔드입니다.

---

## 로컬 실행 방법

### 사전 요구사항

- Java 25+
- MySQL 8.0+
- 로컬 DB에 `members`, `stock_data` 스키마 및 테이블이 생성되어 있어야 합니다.

### 환경변수 설정

`.env` 파일을 만들거나 IDE의 Run Configuration에 아래 환경변수를 등록하세요.

| 변수명 | 설명 | 로컬 기본값 |
|--------|------|------------|
| `DB_PASSWORD` | MySQL 비밀번호 | `local_dev_password` |
| `JWT_SECRET` | JWT 서명 키 (32자 이상) | `antsight-dev-secret-key-must-be-at-least-32-bytes` |
| `CORS_ORIGINS` | 허용할 프론트엔드 출처 (콤마 구분) | `http://localhost:3000` |

> 로컬에서는 위 변수를 생략해도 기본값으로 구동됩니다.

### 실행

```bash
./gradlew bootRun
```

서버가 뜨면 `http://localhost:7689/api/health` 로 확인합니다.

### 초기 관리자 계정

서버 최초 구동 시 자동 생성됩니다.

| 항목 | 값 |
|------|----|
| email | admin@antsight.com |
| password | Admin2026! |
| role | ADMIN |

---

## 운영 배포 환경변수

```bash
SPRING_PROFILES_ACTIVE=prod

RDS_HOST=your-rds-endpoint.rds.amazonaws.com
DB_PASSWORD=your_db_password

JWT_SECRET=your-32-chars-or-longer-secret-key

CORS_ORIGINS=https://your-frontend.com
```

> `JWT_SECRET`은 반드시 32바이트 이상이어야 합니다.

---

## API 엔드포인트 요약

### 인증 불필요

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/health` | 서버 및 DB 상태 확인 |
| `POST` | `/api/auth/signup` | 회원가입 (즉시 로그인 가능) |
| `POST` | `/api/auth/login` | 로그인 → JWT 발급 |

### 인증 필요 (Authorization: Bearer {token})

#### 인증

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/auth/me` | 현재 로그인 사용자 정보 |

#### 주식

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/stocks/search?q={검색어}&limit={N}` | 종목 검색 (기본 limit=20) |
| `GET` | `/api/stocks/active` | 자동화 대상 활성 종목 100개 |
| `GET` | `/api/stocks/{ticker}` | 종목 단건 조회 |

#### 관리자 (ADMIN 전용)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/admin/users` | 전체 회원 목록 조회 |
| `PATCH` | `/api/admin/users/{id}/block` | 사용자 차단 (`is_approved=false`) |
| `PATCH` | `/api/admin/users/{id}/unblock` | 차단 해제 (`is_approved=true`) |

#### 북마크

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/bookmarks` | 내 북마크 목록 (종목 정보 포함) |
| `POST` | `/api/bookmarks` | 북마크 추가 `{ "ticker": "005930" }` |
| `DELETE` | `/api/bookmarks/{ticker}` | 북마크 삭제 |

#### 개미지수 / 캔들

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/ant-index/{ticker}/latest` | 종목 단건 최신 개미지수 |
| `GET` | `/api/ant-index/{ticker}?from=&to=&interval=15m\|1h\|1d` | 시계열 (interval 기본 `15m`) |
| `GET` | `/api/ant-index/ranking?window=15m\|1h\|24h&direction=positive\|negative&limit=10` | 순위 |
| `GET` | `/api/candles/{ticker}?from=&to=&withAntIndex=true` | 5분봉 + 같은 15분 버킷의 개미지수 LEFT JOIN (최대 7일) |

##### 호출 예시 (005930)

```bash
TOKEN=...   # POST /api/auth/login 으로 발급

curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:7689/api/ant-index/005930/latest"

curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:7689/api/ant-index/005930?from=2026-05-14T09:00:00&interval=1h"

curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:7689/api/ant-index/ranking?window=24h&direction=positive&limit=10"

curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:7689/api/candles/005930?from=2026-05-14T09:00:00&to=2026-05-14T15:30:00&withAntIndex=true"
```

---

## 에러 응답 형식

```json
{
  "success": false,
  "data": null,
  "message": "에러 메시지"
}
```

| HTTP 코드 | 상황 |
|-----------|------|
| `400` | 입력값 검증 실패 |
| `401` | 인증 실패 (비밀번호 불일치, 토큰 없음/만료) |
| `403` | 관리자 승인 대기 중 |
| `404` | 리소스 없음 |
| `409` | 중복 (이메일, 북마크) |
| `500` | 서버 오류 |

---

## 데이터베이스 구조

| 스키마 | 테이블 | 설명 |
|--------|--------|------|
| `members` | `users` | 회원 정보 |
| `members` | `bookmarks` | 사용자별 북마크 |
| `stock_data` | `stocks` | 종목 정보 |
| `stock_data` | `candles_5min` | 5분봉 OHLCV |
| `ant_index` | `scores` | 15분 단위 개미지수 집계 |
| `stockboard` | `posts` | 게시글 + 게시글 단위 추론 점수 |

### cross-schema 접근 권한

개미지수/캔들 API 는 RDS 사용자 (`antsight_app`) 가 다음 스키마에 대한 `SELECT` 권한을 보유해야 동작합니다. 권한이 없으면 애플리케이션 부팅 시 hibernate schema validation 단계에서 `missing table [stockboard.posts]` 같은 에러로 실패합니다.

```sql
GRANT SELECT ON ant_index.*  TO 'antsight_app'@'%';
GRANT SELECT ON stockboard.* TO 'antsight_app'@'%';
FLUSH PRIVILEGES;
```
