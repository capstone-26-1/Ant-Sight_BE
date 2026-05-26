# coding=utf-8
"""
Model A v2 — 6단계: 학생 모델로 stockboard.posts 전량(약 170만건) 추론.

입력 : model_a_v2/ (5단계 산출 학생 모델)
출력 : stockboard.post_labels_v2  (post_id 별 6개 필드)

특징:
  - GPU 배치 추론 (bf16 autocast)
  - 재실행 가능: post_labels_v2 에 이미 있는 id 는 건너뜀
  - 청크 커밋 (InnoDB lock 회피)
  - 진행률·ETA 실시간 출력

사용자 PC(GPU)에서 실행:  python infer_model_a_v2.py
"""
import sys
import json
import time
from pathlib import Path

import torch
import torch.nn as nn
import pymysql
from transformers import AutoTokenizer, AutoModel

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE     = Path(__file__).resolve().parent
MODEL_DIR = BASE / 'model_a_v2'
CFG      = json.load(open(BASE / 'db_config.json', encoding='utf-8'))

BATCH       = 128          # 추론은 학습보다 크게
FETCH_CHUNK = 20_000       # DB 에서 한 번에 읽어올 행 수
COMMIT_CHUNK = 5_000       # INSERT 커밋 단위

PT_MAP = {'분석정보': 0, '반응': 1, '조롱비난': 2, '질문': 3, '잡담홍보': 4}
PT_INV = {v: k for k, v in PT_MAP.items()}
HEADS  = {'post_type': 5, 'stance': 5, 'euphoria': 4,
          'anxiety': 4, 'capitulation': 4, 'anger': 4}


class ModelAV2(nn.Module):
    def __init__(self, base):
        super().__init__()
        self.encoder = AutoModel.from_pretrained(base)
        h = self.encoder.config.hidden_size
        self.drop = nn.Dropout(0.1)
        self.heads = nn.ModuleDict(
            {k: nn.Linear(h, n) for k, n in HEADS.items()})

    def forward(self, input_ids, attention_mask):
        out = self.encoder(input_ids=input_ids, attention_mask=attention_mask)
        cls = self.drop(out.last_hidden_state[:, 0])
        return {k: head(cls) for k, head in self.heads.items()}


def conn():
    return pymysql.connect(host=CFG['host'], port=CFG.get('port', 3306),
                           user=CFG['user'], password=CFG['password'],
                           charset=CFG.get('charset', 'utf8mb4'))


def main():
    if not MODEL_DIR.exists():
        print(f'[오류] {MODEL_DIR.name}/ 없음 — 5단계(train_model_a_v2.py) 먼저 실행')
        sys.exit(1)
    meta = json.load(open(MODEL_DIR / 'meta.json', encoding='utf-8'))
    max_len = meta['max_len']

    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    use_cuda = device == 'cuda'
    print(f'[정보] device={device}, max_len={max_len}')

    tok = AutoTokenizer.from_pretrained(MODEL_DIR)
    model = ModelAV2(meta['base_model']).to(device)
    model.load_state_dict(torch.load(MODEL_DIR / 'model.pt', map_location=device))
    model.eval()
    print('[정보] 학생 모델 로드 완료')

    c = conn()
    with c.cursor() as cur:
        cur.execute("""
            CREATE TABLE IF NOT EXISTS stockboard.post_labels_v2 (
                post_id      BIGINT PRIMARY KEY,
                post_type    VARCHAR(8),
                stance       TINYINT,
                euphoria     TINYINT,
                anxiety      TINYINT,
                capitulation TINYINT,
                anger        TINYINT
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)
        c.commit()
        cur.execute("SELECT COUNT(*) FROM stockboard.post_labels_v2")
        n_done = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM stockboard.posts WHERE title IS NOT NULL")
        n_total = cur.fetchone()[0]
    print(f'[정보] 전체 {n_total:,} / 완료 {n_done:,} / 남음 {n_total - n_done:,}')
    if n_done >= n_total:
        print('[정보] 모두 추론됨')
        c.close()
        return

    # 처리할 id 목록을 한 번에 회수 (버퍼드 — 짧은 단일 쿼리)
    print('[정보] 미처리 id 목록 회수 중 ...', flush=True)
    with c.cursor() as cur:
        cur.execute("""
            SELECT p.id
            FROM stockboard.posts p
            LEFT JOIN stockboard.post_labels_v2 l ON l.post_id = p.id
            WHERE p.title IS NOT NULL AND l.post_id IS NULL
        """)
        todo_ids = [r[0] for r in cur.fetchall()]
    n_todo = len(todo_ids)
    print(f'[정보] 처리 대상 {n_todo:,}건', flush=True)
    if n_todo == 0:
        print('[정보] 모두 추론됨')
        c.close()
        return

    def db_exec(fn):
        """연결 끊김 시 재연결 후 1회 재시도."""
        nonlocal c
        for attempt in range(2):
            try:
                return fn()
            except (pymysql.err.OperationalError,
                    pymysql.err.InterfaceError):
                if attempt == 0:
                    try:
                        c.close()
                    except Exception:
                        pass
                    c = conn()
                else:
                    raise

    t0 = time.time()
    processed = 0
    SELECT_CHUNK = 1000

    for s in range(0, n_todo, SELECT_CHUNK):
        chunk_ids = todo_ids[s:s + SELECT_CHUNK]
        ph = ','.join(['%s'] * len(chunk_ids))

        def _fetch():
            with c.cursor() as cur:
                cur.execute(
                    f"SELECT id, title, text FROM stockboard.posts "
                    f"WHERE id IN ({ph})", chunk_ids)
                return cur.fetchall()
        rows = db_exec(_fetch)

        ids = [r[0] for r in rows]
        texts = [((r[1] or '') + ' ' + (r[2] or '')).strip() for r in rows]
        buf = []
        for i in range(0, len(rows), BATCH):
            bids = ids[i:i + BATCH]
            btxt = texts[i:i + BATCH]
            enc = tok(btxt, padding=True, truncation=True,
                      max_length=max_len, return_tensors='pt')
            enc = {k: v.to(device) for k, v in enc.items()}
            with torch.no_grad(), torch.autocast('cuda', dtype=torch.bfloat16,
                                                 enabled=use_cuda):
                logits = model(enc['input_ids'], enc['attention_mask'])
            pred = {k: logits[k].argmax(-1).cpu().tolist() for k in HEADS}
            for j, pid in enumerate(bids):
                buf.append((
                    pid,
                    PT_INV[pred['post_type'][j]],
                    pred['stance'][j] - 2,          # 0~4 -> -2~+2
                    pred['euphoria'][j],
                    pred['anxiety'][j],
                    pred['capitulation'][j],
                    pred['anger'][j],
                ))

        def _write():
            with c.cursor() as cur:
                cur.executemany(
                    "INSERT IGNORE INTO stockboard.post_labels_v2 "
                    "(post_id,post_type,stance,euphoria,anxiety,"
                    "capitulation,anger) VALUES (%s,%s,%s,%s,%s,%s,%s)", buf)
            c.commit()
        db_exec(_write)

        processed += len(rows)
        if (s // SELECT_CHUNK) % 20 == 0 or processed >= n_todo:
            el = time.time() - t0
            rate = processed / el if el else 0
            eta = (n_todo - processed) / rate if rate else 0
            print(f'[진행] {processed:,}/{n_todo:,} | '
                  f'{rate:.0f}건/s | 경과 {el/60:.1f}분 | ETA {eta/60:.1f}분',
                  flush=True)
    c.close()
    print(f'[완료] 추론 적재 완료 — {processed:,}건, 총 {(time.time()-t0)/60:.1f}분')


if __name__ == '__main__':
    main()
