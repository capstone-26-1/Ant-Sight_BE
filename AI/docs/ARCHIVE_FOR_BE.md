# BE 후배 전달용 압축 패키지 명세

> 본 문서는 *BE 후배에게 전달할 압축 파일에 무엇이 들어가야 하는지* 명세서.
> 전달자(정태웅)와 수령자(BE 후배) 모두 점검 가능하도록 작성.

---

## 0. 한 줄 요약

| 항목 | 내용 |
|---|---|
| **파일명** | `Ant-Sight_AI_deploy_YYYYMMDD.zip` |
| **크기** | 약 200~250MB (Model A 가중치가 대부분) |
| **압축 명령** | `bash make_deploy_archive.sh` (저장소 루트에) |
| **수령 후** | `docs/DEPLOY_AWS_BE_GUIDE.md` 먼저 읽기 |

---

## 1. 압축 파일 안의 폴더 구조

```
Ant-Sight_AI_deploy_YYYYMMDD/
├── README.md                          ← 압축본 README (BE 전용)
├── docs/
│   ├── DEPLOY_AWS_BE_GUIDE.md         ⭐ 메인 가이드 (먼저 읽기)
│   ├── ARCHIVE_FOR_BE.md              ← 본 문서
│   └── handoff/
│       └── 인수인계_ModelAB.md         (Model A·B 기술 상세)
│
├── code/                              ← 추론·집계 코드 (Stage 1~4)
│   ├── infer_model_a_v2.py            ⭐ Model A 추론 (Stage 1)
│   ├── build_ant_index_v2.py          ⭐ 개미지수 합성 (Stage 2)
│   ├── build_market_antindex.py       ⭐ 시장 집계 (Stage 3)
│   ├── normalize_market_antindex.py   ⭐ 정규화 (Stage 3)
│   ├── predict_voljump.py             ⭐ Model B 추론 (Stage 4)
│   ├── train_model_a_v2.py            (참고용 — 재학습 시)
│   ├── train_model_b_voljump.py       (참고용 — 재학습 시)
│   └── eval_model_b_extra.py          (참고용 — 정기 평가용)
│
├── models/                            ← 학습된 가중치 (.gitignore)
│   ├── model_a_v2/                    🤖 423MB · KLUE-RoBERTa
│   │   ├── model.pt
│   │   ├── tokenizer.json
│   │   ├── tokenizer_config.json
│   │   └── meta.json
│   └── model_b/                       🤖 1MB · LightGBM
│       ├── model_b.joblib
│       └── meta.json
│
├── config/                            ← 설정 템플릿
│   ├── db_config.example.json         🔒 실제 자격증명 X
│   └── MODEL_A_V2_RUBRIC.md           (라벨링 기준)
│
└── requirements.txt                   ← 검증된 의존성 버전
```

### ⛔ 압축에서 *제외*되는 것

| 제외 | 이유 |
|---|---|
| `experiments/` | 검증 실험 — 운영에 불필요 |
| `backtest/` | 백테스트 — 운영에 불필요 |
| `debug/` | 임시 디버그 헬퍼 |
| `archived/` | 구버전 |
| `.git/` | git 이력 (BE는 자체 git 운영) |
| `__pycache__/` | Python 캐시 |
| `docs/papers/`, `docs/guide/` | 발표·연구 문서 (운영 무관) |
| `db_config.json` | 실제 자격증명 ⚠ |
| `*.csv` 데이터 캐시 | 재생성 가능 |

---

## 2. requirements.txt 내용 (검증된 버전)

다음을 `Ant-Sight_AI/requirements.txt`로 함께 압축:

```txt
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
```

⚠ **버전 고정 필수.** transformers 4.35는 PyTorch 2.0 호환이며, 그 이상으로 올리면
tokenizer 동작이 다를 수 있음. 본 모델 가중치는 이 조합으로 학습됨.

---

## 3. 압축 자동화 스크립트

압축은 `make_deploy_archive.sh`로 자동화:

```bash
cd Ant-Sight_AI
bash make_deploy_archive.sh
# → Ant-Sight_AI_deploy_YYYYMMDD.zip 생성 (상위 폴더에)
```

이 스크립트는:
1. 위 폴더 구조 그대로 임시 디렉토리에 *복사*
2. 운영 무관 파일 *제외*
3. README·requirements.txt 자동 생성
4. md5 체크섬 함께 출력 (전달 후 무결성 확인)

---

## 4. 전달 후 BE가 즉시 확인할 5가지

압축 풀고 첫 점검:

```bash
unzip Ant-Sight_AI_deploy_*.zip
cd Ant-Sight_AI_deploy_*

# 점검 1: 핵심 파일 존재
ls models/model_a_v2/model.pt && echo "✓ Model A"
ls models/model_b/model_b.joblib && echo "✓ Model B"
ls code/infer_model_a_v2.py && echo "✓ 추론 코드"
ls docs/DEPLOY_AWS_BE_GUIDE.md && echo "✓ 가이드"

# 점검 2: 모델 가중치 무결성 (전달자 알려준 md5와 비교)
md5sum models/model_a_v2/model.pt
md5sum models/model_b/model_b.joblib

# 점검 3: 파일 크기
du -sh models/

# 점검 4: 자격증명 누출 X
grep -r "password" config/db_config.example.json
# → "YOUR_DB_PASSWORD" 등 placeholder만 보여야 함

# 점검 5: requirements.txt 있는지
cat requirements.txt
```

다섯 가지 모두 ✓이어야 가이드의 다음 단계 진행.

---

## 5. 전달 방법 (보안)

### 옵션 A — Google Drive (간편)
1. 압축 zip을 Drive 업로드
2. "링크가 있는 사람만 보기" 권한
3. 링크를 *전화·메신저 직접 메시지*로 전달 (이메일은 보안 약함)
4. 다운로드 후 *24시간 내 링크 삭제*

### 옵션 B — S3 Presigned URL (권장)
```bash
aws s3 cp Ant-Sight_AI_deploy_YYYYMMDD.zip s3://ant-sight-handoff/
aws s3 presign s3://ant-sight-handoff/Ant-Sight_AI_deploy_YYYYMMDD.zip --expires-in 86400
```
24시간 만료 URL → 후배에게 전달.

### 옵션 C — 직접 USB / 외장 디스크
- 가장 안전 (네트워크 노출 0)
- 단 BE가 같은 공간에 있을 때만 가능

⚠ **금지**:
- 카카오톡·이메일 첨부 (250MB 한도 + 보관됨)
- 공개 파일 공유 (Mediafire 등)
- 자격증명 파일 동봉 (`db_config.json`은 절대 포함 X)

---

## 6. 무결성 체크 (전달자·수령자 양쪽)

전달자 측에서 압축 후:
```bash
md5sum Ant-Sight_AI_deploy_*.zip > checksum.txt
md5sum models/model_a_v2/model.pt models/model_b/model_b.joblib >> checksum.txt
cat checksum.txt
# 이 출력을 별도 메시지로 BE에게 전달
```

수령자 측에서 풀기 전:
```bash
md5sum Ant-Sight_AI_deploy_*.zip
# 전달자가 알려준 값과 일치하는지 확인
```

다르면 *재전송*. 일치하면 압축 풀고 가이드의 5점검.

---

## 7. 자주 묻는 질문

### Q1. 모델 가중치를 git에 안 넣고 압축으로만 전달하는 이유?
- 423MB → GitHub LFS 한도(1GB) 안에 들어가나 *무료 LFS는 1GB/월*
- git pull/push 시마다 423MB 전송 → 비효율
- 압축 전달 1회 + 재학습 시에만 새 가중치 발행이 효율적

### Q2. BE 측에서 모델 재학습 필요?
- 단기 (3개월): 불필요. 동봉된 가중치 그대로 사용
- 중기 (6개월~): 새 데이터로 정기 평가 → 성능 저하 시 재학습 검토
- 재학습은 *AI 담당(정태웅)*에게 요청. BE가 직접 안 함

### Q3. requirements.txt의 버전을 올려도 되나?
- ❌ **메이저 버전은 절대 금지** (torch 2.x→3.x 등)
- ✅ 패치 버전은 가능 (1.24.3 → 1.24.5)
- 보안 패치는 검토 후 적용
- 변경 시 *AI 담당*과 합의

### Q4. 메모리 부족이면?
- 우선 batch_size 줄이기 (코드 내 `BATCH_SIZE = 500`)
- 그래도 부족 → 인스턴스 업그레이드 (t3.medium → t3.large)
- 또는 모델 quantization (별도 작업, AI 담당과 협의)

---

## 8. 최종 체크리스트 (전달 직전)

전달자(정태웅) 작업:
- [ ] `make_deploy_archive.sh` 실행으로 zip 생성
- [ ] zip 안에 `db_config.json` 없는지 확인 (`unzip -l ... | grep db_config`)
- [ ] zip 크기 200~250MB 범위 확인
- [ ] md5 체크섬 별도 메시지 작성
- [ ] 전달 방법 (Drive/S3/USB) 선택·실행
- [ ] BE 후배에게 가이드 위치 안내: `docs/DEPLOY_AWS_BE_GUIDE.md`

수령자(BE 후배) 작업:
- [ ] zip 다운로드 후 md5 확인
- [ ] 압축 풀고 5점검 (위 §4)
- [ ] 가이드 §0~§3 따라 EC2 셋업
- [ ] 가이드 §5 4단계 추론 파이프라인 작동 점검
- [ ] 가이드 §6 자동화 적용 (cron 또는 systemd)
- [ ] 가이드 §7 보안 체크리스트 점검
- [ ] 가이드 §9 실수 회피 체크리스트 점검

---

*작성: 정태웅 · 2026-05-21 · Ant-Sight 캡스톤*
