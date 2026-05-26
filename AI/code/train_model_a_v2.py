# coding=utf-8
"""
Model A v2 — 5단계: KLUE-RoBERTa-base 멀티태스크 파인튜닝 (학생 모델).

입력 : label_sample_labeled.csv (3·4단계 산출, 25,000건 라벨)
출력 : model_a_v2/  (학생 모델 — 6개 필드 동시 예측)

구조: klue/roberta-base 공유 인코더 + 6개 분류 헤드
  post_type(5) / stance(5) / euphoria(4) / anxiety(4) / capitulation(4) / anger(4)
손실: 6개 CrossEntropy 평균. capitulation 은 희소 -> class weight 적용.

사용자 PC(GPU)에서 실행:  python train_model_a_v2.py
KLUE-RoBERTa-base(110M) 는 16GB 에서 풀 파인튜닝 매우 가벼움 (epoch 당 수 분).
"""
import sys
import json
import random
from pathlib import Path

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset
from transformers import AutoTokenizer, AutoModel

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE       = Path(__file__).resolve().parent
IN_CSV     = BASE / 'label_sample_labeled.csv'
OUT_DIR    = BASE / 'model_a_v2'
BASE_MODEL = 'klue/roberta-base'

MAX_LEN    = 256
BATCH      = 32
EPOCHS     = 4
LR         = 2e-5
VAL_FRAC   = 0.10
SEED       = 20260519

# 라벨 인코딩
PT_MAP  = {'분석정보': 0, '반응': 1, '조롱비난': 2, '질문': 3, '잡담홍보': 4}
PT_INV  = {v: k for k, v in PT_MAP.items()}
HEADS   = {'post_type': 5, 'stance': 5, 'euphoria': 4,
           'anxiety': 4, 'capitulation': 4, 'anger': 4}

random.seed(SEED); np.random.seed(SEED); torch.manual_seed(SEED)


class ModelAV2(nn.Module):
    def __init__(self, base=BASE_MODEL):
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


def main():
    if not IN_CSV.exists():
        print(f'[오류] {IN_CSV.name} 없음'); sys.exit(1)
    df = pd.read_csv(IN_CSV, encoding='utf-8-sig', dtype={'stock_code': str})
    df = df[df['post_type'].isin(PT_MAP)].copy()
    df['full_text'] = (df['title'].fillna('') + ' '
                       + df['text'].fillna('')).str.strip()
    df = df[df['full_text'].str.len() > 0].reset_index(drop=True)
    print(f'[정보] 학습 데이터 {len(df):,}건')

    # 라벨 텐서
    y = {}
    y['post_type']    = df['post_type'].map(PT_MAP).astype(int).values
    y['stance']       = (pd.to_numeric(df['stance']) + 2).astype(int).values
    for d in ('euphoria', 'anxiety', 'capitulation', 'anger'):
        y[d] = pd.to_numeric(df[d]).clip(0, 3).astype(int).values

    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    print(f'[정보] device={device}, base={BASE_MODEL}')
    tok = AutoTokenizer.from_pretrained(BASE_MODEL)
    enc = tok(df['full_text'].tolist(), padding='max_length', truncation=True,
              max_length=MAX_LEN, return_tensors='pt')

    n = len(df)
    idx = np.random.permutation(n)
    n_val = int(n * VAL_FRAC)
    val_idx, tr_idx = idx[:n_val], idx[n_val:]
    print(f'[정보] train {len(tr_idx):,} / val {len(val_idx):,}')

    def make_ds(ids):
        return TensorDataset(
            enc['input_ids'][ids], enc['attention_mask'][ids],
            *[torch.tensor(y[k][ids]) for k in HEADS])
    tr_dl = DataLoader(make_ds(tr_idx), batch_size=BATCH, shuffle=True)
    va_dl = DataLoader(make_ds(val_idx), batch_size=BATCH)

    # capitulation class weight (희소 대응)
    cw = {}
    for k in HEADS:
        cnt = np.bincount(y[k][tr_idx], minlength=HEADS[k]).astype(float)
        w = cnt.sum() / (len(cnt) * np.clip(cnt, 1, None))
        cw[k] = torch.tensor(w, dtype=torch.float32, device=device)
    lossfn = {k: nn.CrossEntropyLoss(weight=cw[k]) for k in HEADS}

    model = ModelAV2().to(device)
    opt = torch.optim.AdamW(model.parameters(), lr=LR)
    use_cuda = device == 'cuda'

    for ep in range(1, EPOCHS + 1):
        model.train()
        tot = 0.0
        for bi, batch in enumerate(tr_dl):
            ids, am = batch[0].to(device), batch[1].to(device)
            labs = {k: batch[2 + i].to(device) for i, k in enumerate(HEADS)}
            opt.zero_grad()
            with torch.autocast('cuda', dtype=torch.bfloat16, enabled=use_cuda):
                logits = model(ids, am)
                loss = sum(lossfn[k](logits[k].float(), labs[k])
                           for k in HEADS) / len(HEADS)
            loss.backward()
            opt.step()
            tot += loss.item()
            if bi % 100 == 0:
                print(f'  ep{ep} {bi}/{len(tr_dl)} loss={loss.item():.4f}',
                      flush=True)
        # 검증
        model.eval()
        correct = {k: 0 for k in HEADS}
        nval = 0
        with torch.no_grad():
            for batch in va_dl:
                ids, am = batch[0].to(device), batch[1].to(device)
                labs = {k: batch[2 + i] for i, k in enumerate(HEADS)}
                with torch.autocast('cuda', dtype=torch.bfloat16,
                                    enabled=use_cuda):
                    logits = model(ids, am)
                for k in HEADS:
                    pred = logits[k].argmax(-1).cpu()
                    correct[k] += (pred == labs[k]).sum().item()
                nval += ids.size(0)
        accs = {k: correct[k] / nval for k in HEADS}
        print(f'[epoch {ep}] train_loss={tot/len(tr_dl):.4f}  '
              f'val_acc: ' + '  '.join(f'{k}={accs[k]:.3f}' for k in HEADS))

    # 저장
    OUT_DIR.mkdir(exist_ok=True)
    torch.save(model.state_dict(), OUT_DIR / 'model.pt')
    tok.save_pretrained(OUT_DIR)
    json.dump({'base_model': BASE_MODEL, 'max_len': MAX_LEN,
               'heads': HEADS, 'pt_map': PT_MAP},
              open(OUT_DIR / 'meta.json', 'w', encoding='utf-8'),
              ensure_ascii=False, indent=2)
    print(f'[완료] 학생 모델 저장 -> {OUT_DIR.name}/  (model.pt, meta.json, tokenizer)')


if __name__ == '__main__':
    main()
