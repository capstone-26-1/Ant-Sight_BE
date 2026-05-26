# Ant-Sight AI 배포 패키지 (BE 전용)

이 저장소는 **AWS EC2에 Model A v2 + Model B 추론기를 배포**하기 위한 패키지입니다.

> **배포 상태: 완료 (2026-05-26)**
> AI EC2에 전체 파이프라인(Stage 1~4) 배포 및 자동화 완료. 산출물(개미지수·시장지수·변동성경보) RDS 적재 중.
> 실제 배포 환경이 원래 가이드와 일부 달라진 부분은 아래 "배포 중 변경 사항"을 참고하세요.

---

## 배포 중 변경 사항 (실제 적용 기준)

원본 가이드(`docs/DEPLOY_AWS_BE_GUIDE.md`)는 배포 전 작성된 것으로, 실제 배포에서 환경 차이로 아래와 같이 조정되었습니다.

| 항목 | 원본 가이드 | 실제 배포 |
|---|---|---|
| OS | Ubuntu 22.04 | **Ubuntu 24.04** (22.04 일반 AMI 부재) |
| Python | 3.10 | **3.12** (24.04 기본) |
| 의존성 | `requirements.txt` 버전 고정 | **최신 버전 설치** (3.12 호환, torch는 CPU 전용) |
| db_config.json 위치 | 루트 | **`code/db_config.json`** (코드가 `Path(__file__).parent` 기준 탐색) |
| 모델 경로 | `models/model_a_v2`, `models/model_b` | **`code/` 안에 심볼릭 링크** |
| Stage 2 운영 | `build_ant_index_v2.py` | **`build_ant_index_incremental.py` 신규 작성** |
| Stage 4 주가 로딩 | `candles_5min` 전체 | **최근 60일만** (`WHERE candle_time >= DATE_SUB(NOW(), INTERVAL 60 DAY)`) |

### Stage 2 — 증분 스크립트 신규 작성

`build_ant_index_v2.py`는 `scores_v2`를 DROP 후 전체(9년치) 재계산하므로 운영 cron에 부적합합니다. 계산식은 100% 동일하게 유지하되 **최근 구간만 UPSERT**하는 `build_ant_index_incremental.py`를 추가했습니다.

- 원본은 백필(최초 1회)용으로 보존
- 운영 cron은 증분 버전 사용
- 변경점: 입력 범위 제한(`WHERE timestamp >= cutoff`) + 저장 방식(`DROP+INSERT` → `INSERT ... ON DUPLICATE KEY UPDATE`)
- 사용: `python code/build_ant_index_incremental.py --hours 6` 또는 `--since 2026-05-20`

### Stage 4 — 주가 쿼리 범위 제한

`predict_voljump.py`가 `candles_5min` 전체(약 1670만 행)를 읽어 t3.medium(4GB)에서 OOM 발생. 추론 피처 최대 윈도우가 20거래일이므로 최근 60일만 읽도록 `WHERE` 추가. 계산 결과는 동일(필요 윈도우 전부 커버), 메모리는 약 47만 행 수준으로 감소.

---

## 현재 운영 구조

```
[크롤러 EC2]
  posts            <- 5분 cron (직접 INSERT)

[주가 파이프라인]
  candles_5min     <- 최신화 중

[AI EC2 - antsight-ai]
  Stage 1: post_labels_v2        <- 5분 cron   (infer_model_a_v2.py)
  Stage 2: scores_v2 개미지수     <- 15분 cron  (build_ant_index_incremental.py)
  Stage 3: market_index(_norm)    <- 하루 1회   (build_market + normalize)
  Stage 4: voljump_alert 경보     <- 하루 1회   (predict_voljump.py)

[RDS - stockboard / ant_index]
  프론트 조회용 산출물 적재 완료
```

### cron 구성 (AI EC2)

```cron
# Stage 1 - Model A 라벨링 (5분)
*/5 * * * * /usr/bin/flock -n /tmp/infer_a.lock -c '... infer_model_a_v2.py ...'

# Stage 2 - 개미지수 증분 (15분)
*/15 * * * * /usr/bin/flock -n /tmp/ant_index.lock -c '... build_ant_index_incremental.py --hours 6 ...'

# Stage 3,4 - 시장지수 + 변동성경보 (매일 04:00 KST)
0 4 * * * /usr/bin/flock -n /tmp/stage34.lock -c '... build_market_antindex.py && normalize_market_antindex.py && predict_voljump.py ...'
```

---

## 패키지 구성

| 폴더 | 내용 |
|---|---|
| `code/` | 추론·집계 스크립트 (Stage 1~4) + 증분 스크립트 |
| `models/model_a_v2/` | KLUE-RoBERTa 6헤드 가중치 (423MB, git 제외) |
| `models/model_b/` | LightGBM 변동성 점프 분류기 (1MB, git 제외) |
| `config/` | DB 설정 템플릿 (실제 자격증명은 별도 전달) |
| `docs/` | 배포 가이드 + 모델 기술 인수인계 |
| `requirements.txt` | 원본 의존성 버전 (24.04에선 최신 설치 필요) |

### code/ 스크립트

| 파일 | 단계 | 용도 |
|---|---|---|
| `infer_model_a_v2.py` | Stage 1 | 게시글 -> 6필드 라벨링 |
| `build_ant_index_v2.py` | Stage 2 | 개미지수 전체 재계산 (백필용) |
| `build_ant_index_incremental.py` | Stage 2 | 개미지수 증분 갱신 (운영용, 신규) |
| `build_market_antindex.py` | Stage 3 | 시장 개미지수 (공포탐욕지수) |
| `normalize_market_antindex.py` | Stage 3 | 1년 분포 백분위 정규화 |
| `predict_voljump.py` | Stage 4 | 변동성 점프 경보 (주가 60일 제한) |
| `train_*.py` | - | 모델 학습용 (운영 무관) |

---

## 신규 배포 시 작업 순서

1. `docs/DEPLOY_AWS_BE_GUIDE.md` 정독
2. EC2 생성 (Ubuntu 24.04, t3.medium, 30GB)
3. 환경 셋업:
   ```bash
   python3 -m venv .venv && source .venv/bin/activate
   pip install torch --index-url https://download.pytorch.org/whl/cpu
   pip install transformers tokenizers lightgbm scikit-learn pandas numpy scipy pymysql joblib
   ```
4. 모델 심볼릭 링크:
   ```bash
   ln -s ../models/model_a_v2 code/model_a_v2
   ln -s ../models/model_b code/model_b
   ```
5. DB 설정: `cp config/db_config.example.json code/db_config.json` + 자격증명 입력 + `chmod 600`
6. 백필: `infer_model_a_v2.py` -> `build_ant_index_v2.py` (최초 1회, 시간 소요)
   - CPU 추론이 느리면 t3 unlimited 모드 임시 사용 후 복귀
7. Stage 3,4 수동 1회: `build_market_antindex.py` -> `normalize_market_antindex.py` -> `predict_voljump.py`
8. cron 등록 (위 "cron 구성" 참고)

---

## 문의

작업 중 막히면:
- `docs/DEPLOY_AWS_BE_GUIDE.md` 트러블슈팅 먼저 확인
- AI 담당(정태웅) / 배포·인프라 담당(이준서)에게 문의

주의 - 하지 말 것:
- `db_config.json` 또는 모델 파일을 git에 commit (`.gitignore`로 차단됨)
- 모델 파일 경로 임의 변경 (심볼릭 링크 구조 유지)
