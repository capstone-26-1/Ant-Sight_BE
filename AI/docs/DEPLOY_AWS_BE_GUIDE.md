# Ant-Sight AWS 배포 가이드 (BE 담당자용)

> 캡스톤 프로젝트 Ant-Sight의 AI 모듈(Model A v2, Model B)을 AWS EC2에 올려
> **자동으로 개미지수와 변동성 점프 경보를 생성·DB 적재**하는 시스템 구축 가이드.
>
> 대상: BE 담당 후배
> 작성: 정태웅 · 2026-05-21
> 분량: 약 700줄 (실수 회피 + 트러블슈팅 포함)

---

## 📌 시작 전 — 받은 파일 점검 (Mandatory)

압축 풀고 가장 먼저 다음 5개 확인. 하나라도 빠지면 배포 불가:

```bash
unzip Ant-Sight_AI_deploy.zip
cd Ant-Sight_AI_deploy

# 점검 5가지
[ -f models/model_a_v2/model.pt ]              && echo "✓ Model A 가중치 (423MB)"
[ -f models/model_b/model_b.joblib ]           && echo "✓ Model B 가중치 (1MB)"
[ -f config/db_config.example.json ]           && echo "✓ DB 설정 템플릿"
[ -f docs/DEPLOY_AWS_BE_GUIDE.md ]             && echo "✓ 본 가이드"
[ -d code/ ]                                   && echo "✓ 추론 코드"
```

받지 못한 게 있으면 **반드시 전달자에게 요청 후 시작**. 일부만으로 진행 금지.

---

## 0. 한 페이지 요약

| 항목 | 내용 |
|---|---|
| **목표** | 매일·매시간 자동으로 (i) 새 게시글 라벨링 → 개미지수 갱신, (ii) 변동성 점프 경보 생성 → DB 적재 |
| **핵심 모델** | Model A v2 (KLUE-RoBERTa, 423MB, 텍스트 라벨링) · Model B (LightGBM, 1MB, 변동성 점프) |
| **인프라** | AWS EC2 + RDS MySQL + (옵션) S3 / CloudWatch |
| **권장 EC2** | `t3.medium` (Model B + 가벼운 Model A) 또는 `g4dn.xlarge` (Model A GPU 가속) |
| **자동화** | cron (단순) 또는 systemd timer (권장) 또는 EventBridge + Lambda 트리거 |
| **예상 비용** | t3.medium 기준 ≈ $30/월 + RDS + 데이터전송 (학생 캡스톤 수준 $40~60/월) |

---

## 1. 시스템 아키텍처

```
┌──────────────────────────────────────────────────────────────┐
│                       AWS 인프라                             │
│                                                              │
│  ┌─────────────┐         ┌───────────────────────────┐       │
│  │  RDS MySQL  │◀────────│  EC2 (Ant-Sight 추론기)   │       │
│  │             │         │                           │       │
│  │ stockboard. │ posts   │  ┌──────────────────────┐ │       │
│  │  posts ─────┼─────────┼──▶ Model A v2 추론     │ │       │
│  │             │ labels  │  │  (게시글 → 6필드)    │ │       │
│  │ post_labels_◀─────────┼──┘                      │ │       │
│  │  v2         │         │                           │       │
│  │             │ labels  │  ┌──────────────────────┐ │       │
│  │ ant_index.  │─────────┼──▶ 개미지수 합성        │ │       │
│  │  scores_v2 ◀┼─────────┼──┘  (15분 버킷)         │ │       │
│  │             │         │                           │       │
│  │ market_     │ market  │  ┌──────────────────────┐ │       │
│  │  index_norm◀┼─────────┼──▶ 시장 정규화          │ │       │
│  │             │         │  └──────────────────────┘ │       │
│  │             │ feats   │  ┌──────────────────────┐ │       │
│  │ candles_5min┼─────────┼──▶ Model B 변동성 점프  │ │       │
│  │             │ alerts  │  │  (LightGBM)          │ │       │
│  │ voljump_    ◀─────────┼──┘                      │ │       │
│  │  alert      │         │                           │       │
│  └─────────────┘         └───────────────────────────┘       │
│        ▲                                                     │
│        │ 조회                                                │
│  ┌─────┴────────┐                                            │
│  │  BE 서버     │  ─→  FE (사이트 사용자)                    │
│  │  (FastAPI 등)│      개미지수·경보 UI                      │
│  └──────────────┘                                            │
└──────────────────────────────────────────────────────────────┘
```

**핵심 흐름 (시간 순):**
1. 크롤러가 `stockboard.posts`에 새 게시글 INSERT (별도 시스템)
2. **EC2 추론기**가 주기적으로 unlabeled 게시글 추출 → Model A v2 추론 → `post_labels_v2` 적재
3. 라벨 모이면 → 15분 버킷 합성 → `scores_v2` 갱신
4. 일 종료 시 → 시장 단위 집계 + 정규화 → `market_index_norm` 갱신
5. 일 종료 시 → Model B 추론 → `voljump_alert` 갱신
6. BE 서버가 위 테이블 조회 → FE에 노출

---

## 2. EC2 인스턴스 사양

### 2-1. 권장안 비교

| 시나리오 | 인스턴스 | 사양 | 시간당 | 월 비용 | 적합도 |
|---|---|---|---|---|---|
| **최소 (Model B만)** | `t3.small` | 2 vCPU / 2GB | $0.023 | $17 | △ Model A 처리 느림 |
| **권장 (캡스톤)** | `t3.medium` | 2 vCPU / 4GB | $0.046 | $34 | ✅ Model A CPU 추론 + Model B + 집계 |
| **빠른 처리** | `t3.large` | 2 vCPU / 8GB | $0.083 | $60 | ✅ 메모리 여유, 동시 작업 가능 |
| **GPU 가속** | `g4dn.xlarge` | 4 vCPU / 16GB / T4 | $0.526 | $380 | ⚠ Model A 빠름, 비쌈 |

**캡스톤 → `t3.medium` 권장.** GPU는 비용 대비 효익 적음 (Model A 일 1회 배치면 충분).

### 2-2. 추가 사양
- **OS**: Ubuntu 22.04 LTS (Amazon Linux 2023도 가능, 의존성 약간 다름)
- **EBS**: gp3 30GB 이상 (Model A 423MB + 의존성 ~3GB + 데이터 캐시 여유)
- **Region**: RDS와 동일 region (`ap-northeast-2` 서울) — 데이터 전송 비용 절감
- **Security Group**:
  - Inbound: SSH(22) — 본인 IP만
  - Outbound: 443 (PyPI·HuggingFace), 3306 (RDS)
- **IAM Role**: (옵션) S3 접근, CloudWatch 로그

---

## 3. 환경 셋업 (EC2 첫 부팅 후)

### 3-1. 시스템 패키지

```bash
sudo apt-get update
sudo apt-get install -y python3.10 python3.10-venv python3-pip \
                        git curl unzip build-essential
```

⚠ **주의**: Ubuntu 22.04 기본 Python은 3.10. transformers 등 호환 OK.

### 3-2. 작업 디렉토리 생성·가상환경

```bash
mkdir -p /home/ubuntu/ant-sight
cd /home/ubuntu/ant-sight

# 받은 압축 풀기
unzip /tmp/Ant-Sight_AI_deploy.zip
# 또는 S3에서 받기
# aws s3 cp s3://your-bucket/Ant-Sight_AI_deploy.zip .

cd Ant-Sight_AI_deploy

# 가상환경
python3.10 -m venv .venv
source .venv/bin/activate
```

### 3-3. Python 의존성 (검증된 버전)

`requirements.txt`로 묶어 동시 설치:

```bash
cat > requirements.txt << 'EOF'
torch==2.0.1
transformers==4.35.0
tokenizers==0.14.1
lightgbm==4.1.0
scikit-learn==1.3.0
pandas==2.0.3
numpy==1.24.3
scipy==1.11.1
pymysql==1.1.0
joblib==1.3.2
EOF

pip install -r requirements.txt
```

⚠ **CPU 전용 PyTorch가 더 가볍다** (1GB → 200MB). GPU 안 쓸 거면:
```bash
pip install torch==2.0.1+cpu --index-url https://download.pytorch.org/whl/cpu
```

### 3-4. DB 자격증명 설정

```bash
cp config/db_config.example.json db_config.json
vim db_config.json
```

```json
{
  "host": "your-rds-endpoint.ap-northeast-2.rds.amazonaws.com",
  "port": 3306,
  "user": "ant_sight_inference_user",
  "password": "STRONG_PASSWORD_FROM_SECRETS_MANAGER",
  "database": "stock_data",
  "charset": "utf8mb4"
}
```

⚠ **보안 최우선**:
- `db_config.json`은 **절대 git commit 금지** (.gitignore에 이미 등록)
- **권장**: AWS Secrets Manager에 자격증명 저장 후 코드에서 boto3로 fetch
- 차선: SSM Parameter Store
- 최후: 파일 권한 `chmod 600 db_config.json`

### 3-5. 작동 점검

```bash
# DB 연결 확인
python -c "
import json, pymysql
cfg = json.load(open('db_config.json'))
c = pymysql.connect(host=cfg['host'], port=cfg['port'], user=cfg['user'],
                    password=cfg['password'], charset='utf8mb4')
cur = c.cursor()
cur.execute('SELECT COUNT(*) FROM stockboard.posts')
print('총 게시글:', cur.fetchone()[0])
c.close()
"

# Model A 로딩 확인 (메모리 ~1GB 필요)
python -c "
import torch
from transformers import AutoModel, AutoTokenizer
print('PyTorch 버전:', torch.__version__, 'CUDA:', torch.cuda.is_available())
m = torch.load('models/model_a_v2/model.pt', map_location='cpu')
print('Model A 로딩 성공')
"

# Model B 로딩 확인
python -c "
import joblib
mb = joblib.load('models/model_b/model_b.joblib')
print('Model B 트리:', mb.booster_.num_trees(), '피처:', mb.booster_.num_feature())
"
```

세 점검 모두 통과해야 다음 단계 진행.

---

## 4. DB 스키마 (참조)

### 4-1. 입력 테이블 (BE/크롤러가 관리)
```sql
-- stockboard.posts: 원본 게시글 (크롤러가 INSERT)
CREATE TABLE stockboard.posts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(10),
    posted_at DATETIME,
    title TEXT,
    body TEXT,
    -- ... 기타 메타
    INDEX idx_stock_time (stock_code, posted_at)
);
```

### 4-2. 추론 출력 테이블 (Ant-Sight 추론기가 관리)
```sql
-- stockboard.post_labels_v2: Model A 추론 결과
CREATE TABLE stockboard.post_labels_v2 (
    post_id INT PRIMARY KEY,
    post_type TINYINT,        -- 0~4
    stance TINYINT,           -- -2~+2
    euphoria TINYINT,         -- 0~3
    anxiety TINYINT,
    capitulation TINYINT,
    anger TINYINT,
    labeled_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id) REFERENCES stockboard.posts(id)
);

-- ant_index.scores_v2: 15분 버킷 개미지수
CREATE TABLE ant_index.scores_v2 (
    stock_code VARCHAR(10),
    bucket_time DATETIME,
    post_count INT,
    ant_index FLOAT,
    greed_raw FLOAT,
    fear_raw FLOAT,
    mean_anger FLOAT,
    std_stance FLOAT,
    analysis_ratio FLOAT,
    PRIMARY KEY (stock_code, bucket_time)
);

-- ant_index.market_index_norm: 시장 정규화 개미지수
CREATE TABLE ant_index.market_index_norm (
    date DATE PRIMARY KEY,
    norm_index FLOAT,
    norm_zone VARCHAR(10)  -- 극공포/공포/중립/탐욕/극탐욕
);

-- ant_index.voljump_alert: Model B 경보
CREATE TABLE ant_index.voljump_alert (
    stock_code VARCHAR(10),
    target_date DATE,
    jump_prob FLOAT,
    status VARCHAR(20),  -- VALID/LOWCONF/OOU/STALE
    predicted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stock_code, target_date)
);
```

⚠ **테이블이 이미 존재하면 ALTER로 컬럼 추가**. 새 환경이면 위 DDL로 생성.

---

## 5. 추론 파이프라인 (4단계)

### Stage 1 — Model A v2 라벨링 (게시글 → 6필드)

```bash
# 사용: 미라벨 게시글을 배치로 처리
cd /home/ubuntu/ant-sight/Ant-Sight_AI_deploy
source .venv/bin/activate
python code/infer_model_a_v2.py
```

내부 동작:
1. `stockboard.posts` LEFT JOIN `post_labels_v2`로 미라벨 ID 추출
2. 1,000개씩 배치 추론 (CPU 기준 약 3,000건/분)
3. `post_labels_v2`에 INSERT

**처리량 (t3.medium CPU):**
- 1,000건 ≈ 20초
- 10,000건 ≈ 3~4분
- 일 평균 신규 게시글 5,000~20,000건 가정 시 충분

### Stage 2 — 개미지수 합성 (라벨 → 15분 버킷)

```bash
python code/build_ant_index_v2.py
```

- `post_labels_v2` + `posts` 조인 → 15분 버킷 집계
- `scores_v2`에 INSERT (ON DUPLICATE KEY UPDATE)
- 처리 시간 < 1분

### Stage 3 — 시장 단위 집계·정규화 (일 1회)

```bash
python code/build_market_antindex.py
python code/normalize_market_antindex.py
```

- 시장 전체 가중평균 + 365일 백분위 정규화
- `market_index_norm` 갱신

### Stage 4 — Model B 변동성 점프 경보 (일 1회)

```bash
python code/predict_voljump.py
```

- 가격 7 + 개미지수 4 = 11 피처 추출
- Model B 추론 → `voljump_alert` 갱신
- 상태 플래그: VALID/LOWCONF/OOU/STALE

---

## 6. 자동화 — cron vs systemd timer

### 6-1. cron (단순, 빠른 셋업)

```bash
crontab -e
```

```cron
# Stage 1: Model A 라벨링 (5분마다)
*/5 * * * * cd /home/ubuntu/ant-sight/Ant-Sight_AI_deploy && \
    /home/ubuntu/ant-sight/Ant-Sight_AI_deploy/.venv/bin/python \
    code/infer_model_a_v2.py >> logs/infer_a.log 2>&1

# Stage 2: 개미지수 합성 (15분마다)
*/15 * * * * cd /home/ubuntu/ant-sight/Ant-Sight_AI_deploy && \
    .venv/bin/python code/build_ant_index_v2.py >> logs/aggregate.log 2>&1

# Stage 3·4: 일 1회 16:00 KST (장 종료 후)
0 16 * * 1-5 cd /home/ubuntu/ant-sight/Ant-Sight_AI_deploy && \
    .venv/bin/python code/build_market_antindex.py >> logs/market.log 2>&1 && \
    .venv/bin/python code/normalize_market_antindex.py >> logs/norm.log 2>&1 && \
    .venv/bin/python code/predict_voljump.py >> logs/voljump.log 2>&1

# 로그 일 단위 회전 (일요일 자정)
0 0 * * 0 find /home/ubuntu/ant-sight/Ant-Sight_AI_deploy/logs -name "*.log" \
    -mtime +7 -delete
```

⚠ **시간대 주의**: EC2 시간대를 KST로 설정하거나 cron 시간을 UTC로 환산.
```bash
sudo timedatectl set-timezone Asia/Seoul
```

### 6-2. systemd timer (권장, 신뢰성↑)

cron보다 좋은 점:
- 실패 시 로그 명확
- 한 인스턴스만 실행 보장 (이전 작업 종료 대기)
- `journalctl`로 통합 로그 조회

`/etc/systemd/system/ant-sight-infer-a.service`:
```ini
[Unit]
Description=Ant-Sight Model A inference
After=network.target

[Service]
Type=oneshot
User=ubuntu
WorkingDirectory=/home/ubuntu/ant-sight/Ant-Sight_AI_deploy
ExecStart=/home/ubuntu/ant-sight/Ant-Sight_AI_deploy/.venv/bin/python code/infer_model_a_v2.py
StandardOutput=journal
StandardError=journal
```

`/etc/systemd/system/ant-sight-infer-a.timer`:
```ini
[Unit]
Description=Run Ant-Sight Model A every 5 minutes

[Timer]
OnBootSec=2min
OnUnitActiveSec=5min
Persistent=true

[Install]
WantedBy=timers.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now ant-sight-infer-a.timer

# 상태 확인
systemctl list-timers --all
journalctl -u ant-sight-infer-a -n 50
```

동일 패턴으로 `ant-sight-aggregate`, `ant-sight-voljump` timer 생성.

---

## 7. 보안 체크리스트 (필수)

### 7-1. DB
- [ ] RDS 보안그룹: EC2 보안그룹만 인바운드 허용 (CIDR 0.0.0.0/0 절대 X)
- [ ] DB 계정 분리: 추론기는 INSERT/UPDATE만, FE 조회는 SELECT만
- [ ] 자격증명은 Secrets Manager 또는 chmod 600
- [ ] `db_config.json`이 .gitignore에 등록되어 있는지 확인

### 7-2. EC2
- [ ] SSH 키 인증만 (password 인증 비활성)
- [ ] SSH 보안그룹: 본인 IP만 허용
- [ ] OS 자동 보안 업데이트 활성:
  ```bash
  sudo dpkg-reconfigure -p high unattended-upgrades
  ```
- [ ] EC2 인스턴스 프로파일 IAM Role 최소 권한 원칙

### 7-3. 코드·로그
- [ ] 로그에 자격증명·게시글 본문 PII 출력 금지 (게시글 ID만)
- [ ] 모델 파일은 EBS 암호화된 볼륨에 저장
- [ ] CloudWatch Logs로 시스템 로그 백업 (옵션)

### 7-4. 비용 알람
- [ ] AWS Budgets에 월 $100 한도 알람 설정
- [ ] EC2 인스턴스 정지 자동화 (필요 없을 때):
  ```bash
  # 야간 정지 (KST 02:00 ~ 06:00)
  aws events put-rule --schedule-expression "cron(0 17 * * ? *)"
  ```

---

## 8. 모니터링·로깅

### 8-1. 로그 확인

```bash
# systemd 사용 시
journalctl -u ant-sight-infer-a.service -f         # 실시간
journalctl -u ant-sight-voljump.service --since today

# cron 사용 시 (logs/ 폴더)
tail -f logs/infer_a.log
```

### 8-2. CloudWatch Logs 연결 (옵션)

`/etc/awslogs/awslogs.conf`:
```
[/home/ubuntu/ant-sight/logs/infer_a.log]
file = /home/ubuntu/ant-sight/Ant-Sight_AI_deploy/logs/infer_a.log
log_group_name = /ant-sight/infer-a
log_stream_name = {instance_id}
```

### 8-3. 헬스체크 쿼리

매일 새벽 SLA 확인용:
```sql
-- 어제 라벨링 처리량
SELECT DATE(labeled_at), COUNT(*)
FROM stockboard.post_labels_v2
WHERE labeled_at >= CURDATE() - INTERVAL 1 DAY
GROUP BY DATE(labeled_at);

-- 가장 오래된 미라벨 게시글 (지연 모니터)
SELECT MIN(p.posted_at)
FROM stockboard.posts p
LEFT JOIN stockboard.post_labels_v2 l ON p.id = l.post_id
WHERE l.post_id IS NULL;

-- Model B 경보 최신성
SELECT MAX(target_date), COUNT(*)
FROM ant_index.voljump_alert
WHERE predicted_at >= CURDATE();
```

---

## 9. 실수 회피 체크리스트 (Critical)

후배가 흔히 빠지는 함정. **하나라도 어기면 시스템 깨짐**:

### 9-1. 절대 하지 말 것
- [ ] ❌ `db_config.json`을 git에 commit
- [ ] ❌ 모델 파일을 git에 commit (423MB → 거부됨)
- [ ] ❌ `models/` 폴더 경로 변경 (코드가 상대경로로 참조)
- [ ] ❌ Python 버전 다운그레이드 (3.10 권장, 3.8 미만 X)
- [ ] ❌ `pandas`·`numpy` 메이저 버전 임의 업그레이드 (호환성)
- [ ] ❌ Model A 추론 시 GPU 없는데 `cuda` 강제 (코드는 `map_location='cpu'`)
- [ ] ❌ DB 트랜잭션 없이 대량 INSERT (commit 누락 시 데이터 손실)
- [ ] ❌ 동시 여러 인스턴스에서 같은 추론 실행 (race condition)

### 9-2. 반드시 확인할 것
- [ ] ✅ `db_config.json`의 `password` 필드가 *Example* 값이 아닌지
- [ ] ✅ EC2 시간대가 KST (`timedatectl status`)
- [ ] ✅ EBS 디스크 여유 공간 5GB+ (`df -h /`)
- [ ] ✅ 가상환경 활성화 후 실행 (`which python` → `.venv/bin/python`)
- [ ] ✅ `infer_model_a_v2.py`의 BATCH_SIZE = 1000 (메모리·속도 균형)
- [ ] ✅ Model A 로딩 시간 (첫 실행 30~60초, 이후 빠름)
- [ ] ✅ `post_labels_v2`의 FOREIGN KEY가 작동 (orphan label 방지)

### 9-3. 데이터 정합성
- [ ] ✅ ON DUPLICATE KEY UPDATE 사용 (중복 INSERT 시 갱신)
- [ ] ✅ 추론 후 `labeled_at` 타임스탬프 자동 입력
- [ ] ✅ `scores_v2` PRIMARY KEY (stock_code, bucket_time) 중복 방지

---

## 10. 트러블슈팅

### 10-1. "Model A 메모리 부족 (OOM)"
- 증상: `RuntimeError: CUDA out of memory` 또는 OOM kill
- 원인: 배치 크기 너무 큼 / 메모리 부족
- 해결:
  ```python
  # infer_model_a_v2.py 안에서
  BATCH_SIZE = 500  # 1000에서 줄임
  ```
- 또는 인스턴스 업그레이드 (t3.medium → t3.large)

### 10-2. "Lost connection 2013 (RDS)"
- 증상: 긴 작업 중 DB 연결 끊김
- 원인: RDS wait_timeout, ROllback 미처리
- 해결: `db_exec()` helper에 자동 재연결 로직 (코드에 이미 포함). 추가로:
  ```python
  conn.ping(reconnect=True)
  ```

### 10-3. "Previous unbuffered result"
- 증상: SSCursor 사용 후 같은 커넥션 INSERT 시
- 원인: pymysql SSCursor 충돌
- 해결: SELECT 결과를 미리 `fetchall()` 후 별도 작업 (코드에 이미 적용)

### 10-4. "Python 3.10 + transformers 호환"
- transformers 4.35 → torch 2.0+ 권장
- ImportError 시:
  ```bash
  pip install --upgrade transformers tokenizers torch
  ```

### 10-5. "추론 결과가 모두 같은 값"
- 증상: 모든 게시글 같은 라벨로 분류
- 원인: tokenizer 미로딩 / 모델 가중치 손상
- 해결:
  ```bash
  md5sum models/model_a_v2/model.pt
  # 전달자가 알려준 체크섬과 일치하는지 확인
  ```

### 10-6. "cron 작업이 실행 안 됨"
- 증상: `tail -f logs/infer_a.log`에 새 줄 없음
- 원인:
  - 환경변수 미설정 (cron은 shell 환경 안 읽음)
  - 작업 디렉토리 잘못
  - 가상환경 경로 잘못
- 해결: cron 명령 절대경로 사용 + 로그 확인:
  ```bash
  grep CRON /var/log/syslog | tail -20
  ```

---

## 11. 비용 추정 (월별)

| 항목 | 수량 | 단가 | 월 비용 |
|---|---|---|---|
| EC2 t3.medium (24/7) | 1 | $0.046/h | **$34** |
| EBS gp3 30GB | 1 | $2.40/월 | $2 |
| RDS db.t3.micro (별도) | (BE 책임) | — | (별도) |
| 데이터 전송 (RDS↔EC2 same AZ) | — | $0 | $0 |
| 데이터 전송 (Outbound) | 10GB | $0.09/GB | $1 |
| CloudWatch Logs (옵션) | 5GB | $0.50/GB | $3 |
| **합계 (추론기 부분)** | | | **≈ $40/월** |

**비용 절감 옵션:**
- 야간 정지 (EventBridge 스케줄): $34 → $20/월
- Spot Instance: $34 → $10/월 (단, 종료 가능성)
- Reserved Instance (1년 약정): 30~40% 할인

---

## 12. 다음 단계 (운영 시작 후)

### 12-1. 1주차
- 로그 모니터링 (`journalctl` / CloudWatch)
- 처리량 확인 (위 헬스체크 쿼리)
- 비용 추적 (Cost Explorer)

### 12-2. 1개월차
- 모델 성능 드리프트 점검 (개미지수와 가격 상관 변화)
- 새 데이터로 정기 평가 (`code/eval_model_b_extra.py`)
- 디스크·로그 회전 자동화 확인

### 12-3. 발전 방향
- Model A 재학습 주기 (분기별)
- Model B 변동성 점프 임계값 튜닝
- 추가 종목 확장 (현재 235 → 더 많이)
- FE와 API 연동 (Ant-Sight 사이트 메인)

---

## 13. 연락 (긴급 시)

| 상황 | 연락 |
|---|---|
| 코드·모델 관련 | AI 담당 (정태웅) |
| RDS·인프라 | DevOps 또는 본인 |
| 모델 성능 이상 | AI 담당 (정태웅) |

문서 참조:
- 본 가이드 — `docs/DEPLOY_AWS_BE_GUIDE.md`
- 모델 상세 — `docs/handoff/인수인계_ModelAB.md`
- 방법론 — `docs/guide/Ant-Sight_작업방법_가이드.md`

---

*작성: 정태웅 · 2026-05-21 · CAU CSE Capstone Ant-Sight*
