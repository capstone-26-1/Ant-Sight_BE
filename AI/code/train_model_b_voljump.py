# coding=utf-8
"""
Model B — 변동성 점프(volatility jump) 분류.

검증3에서 '유효종목 + 고밀도 날' 조건일 때 개미지수가 forward 변동성에
일관된 증분(dR2 +0.027)을 보였음. 그 신호가 *점프(tail event)* 에서 더
또렷한지 이진 분류로 확인.

점프 정의: rv(t+1) >= JUMP_X * rv_m5   (다음날 변동성이 최근 5일평균 대비 급등)

대상: 유효종목(active_days>=500, 일평균>=12) + 일별 게시글>=MIN_DAILY_POSTS 날
모델: LightGBM 이진분류. A(가격) vs B(가격+개미). walk-forward OOS AUC / PR-AUC.
"""
import sys
import json
import warnings
from pathlib import Path

import numpy as np
import pandas as pd
import pymysql
import lightgbm as lgb
from sklearn.metrics import roc_auc_score, average_precision_score

warnings.filterwarnings('ignore')
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE = Path(__file__).resolve().parent
CFG  = json.load(open(BASE / 'db_config.json', encoding='utf-8'))

MIN_DAYS        = 500
MIN_PPD         = 12
MIN_DAILY_POSTS = 10
JUMP_X          = 1.5     # 점프 배율


def conn():
    return pymysql.connect(host=CFG['host'], port=CFG.get('port', 3306),
                           user=CFG['user'], password=CFG['password'],
                           charset=CFG.get('charset', 'utf8mb4'))


def build_daily_price(cd):
    cd = cd.sort_values(['stock_code', 'candle_time'])
    cd['day'] = cd['candle_time'].dt.floor('D')
    cd['lret'] = np.log(cd['close_price'] /
                        cd.groupby('stock_code')['close_price'].shift(1))
    g = cd.groupby(['stock_code', 'day'])
    d = g.agg(
        rv     =('lret', lambda x: x.std()),
        day_o  =('open_price', 'first'),
        day_c  =('close_price', 'last'),
        day_h  =('high_price', 'max'),
        day_l  =('low_price', 'min'),
        volume =('volume', 'sum'),
        n_bar  =('lret', 'size'),
    ).reset_index()
    d = d[d['n_bar'] >= 10].copy()
    d['ret']    = d['day_c'] / d['day_o'] - 1.0
    d['absret'] = d['ret'].abs()
    d['hl']     = (d['day_h'] - d['day_l']) / d['day_o']
    d['logvol'] = np.log1p(d['volume'].astype(float))
    return d


def walk_forward(panel, feats, label, test_years):
    aucs, aps = [], []
    for ty in test_years:
        tr = panel[panel['year'] < ty]
        te = panel[panel['year'] == ty]
        if len(tr) < 1000 or len(te) < 200 or te['jump'].nunique() < 2:
            continue
        m = lgb.LGBMClassifier(n_estimators=300, learning_rate=0.05,
                               num_leaves=31, subsample=0.8,
                               colsample_bytree=0.8, min_child_samples=50,
                               random_state=42, verbose=-1)
        m.fit(tr[feats], tr['jump'])
        p = m.predict_proba(te[feats])[:, 1]
        auc = roc_auc_score(te['jump'], p)
        ap  = average_precision_score(te['jump'], p)
        aucs.append(auc); aps.append(ap)
        print(f'    [{label}] {ty}: n={len(te):>6,}  점프율={te["jump"].mean():.3f}'
              f'  AUC={auc:.4f}  PR-AUC={ap:.4f}')
    print(f'  >> [{label}] 평균 AUC={np.mean(aucs):.4f}  '
          f'PR-AUC={np.mean(aps):.4f}\n')
    return np.mean(aucs), np.mean(aps)


def main():
    c = conn()
    print('[0] 유효 종목 선별 ...', flush=True)
    stat = pd.read_sql("""
        SELECT stock_code, SUM(post_count) AS tp,
               COUNT(DISTINCT DATE(bucket_time)) AS ad,
               SUM(post_count)/COUNT(DISTINCT DATE(bucket_time)) AS ppd
        FROM ant_index.scores_v2 WHERE bucket_time >= '2021-04-01'
        GROUP BY stock_code
    """, c)
    valid = stat[(stat['ad'] >= MIN_DAYS) &
                 (stat['ppd'] >= MIN_PPD)]['stock_code'].tolist()
    print(f'    유효 종목 {len(valid)}개')

    print('[1] 5분봉 로딩 ...', flush=True)
    cd = pd.read_sql("""
        SELECT ticker AS stock_code, candle_time,
               open_price, high_price, low_price, close_price, volume
        FROM stock_data.candles_5min
    """, c)
    cd['candle_time'] = pd.to_datetime(cd['candle_time'])
    for col in ('open_price', 'high_price', 'low_price', 'close_price'):
        cd[col] = pd.to_numeric(cd[col])
    ant = pd.read_sql("""
        SELECT stock_code, bucket_time, post_count, ant_index,
               greed_raw, fear_raw, mean_anger, std_stance, analysis_ratio
        FROM ant_index.scores_v2 WHERE bucket_time >= '2021-04-01'
    """, c)
    c.close()
    ant['bucket_time'] = pd.to_datetime(ant['bucket_time'])
    cd  = cd[cd['stock_code'].isin(valid)].copy()
    ant = ant[ant['stock_code'].isin(valid)].copy()

    print('[2] 가격 일단위 피처 ...', flush=True)
    d = build_daily_price(cd).sort_values(['stock_code', 'day'])
    gd = d.groupby('stock_code')
    d['rv_lag1']  = gd['rv'].shift(1)
    d['rv_lag2']  = gd['rv'].shift(2)
    d['rv_m5']    = gd['rv'].shift(1).rolling(5).mean().reset_index(0, drop=True)
    d['ret_lag1'] = gd['ret'].shift(1)
    d['abs_lag1'] = gd['absret'].shift(1)
    d['hl_lag1']  = gd['hl'].shift(1)
    d['vol_lag1'] = gd['logvol'].shift(1)
    d['rv_next']  = gd['rv'].shift(-1)
    PRICE = ['rv_lag1', 'rv_lag2', 'rv_m5', 'ret_lag1',
             'abs_lag1', 'hl_lag1', 'vol_lag1']

    print('[3] 개미지수 일단위 집계 ...', flush=True)
    ant['day'] = ant['bucket_time'].dt.floor('D')
    rows = []
    for (sc, day), g in ant.groupby(['stock_code', 'day']):
        w = g['post_count'].astype(float)
        rows.append((sc, day, w.sum(),
                     np.average(g['ant_index'], weights=w),
                     np.average(g['greed_raw'], weights=w),
                     np.average(g['fear_raw'], weights=w),
                     np.average(g['mean_anger'], weights=w),
                     np.average(g['std_stance'], weights=w),
                     np.average(g['analysis_ratio'], weights=w)))
    a = pd.DataFrame(rows, columns=['stock_code', 'day', 'posts', 'ant',
                                    'greed', 'fear', 'anger', 'disp', 'ana'])
    a = a.sort_values(['stock_code', 'day'])
    ga = a.groupby('stock_code')
    a['d_ant']     = a['ant']   - ga['ant'].shift(1)
    a['d_anger']   = a['anger'] - ga['anger'].shift(1)
    a['log_posts'] = np.log1p(a['posts'])
    ANT = ['ant', 'greed', 'fear', 'anger', 'disp', 'ana',
           'log_posts', 'd_ant', 'd_anger']

    panel = d.merge(a, on=['stock_code', 'day'], how='left')
    panel = panel.dropna(subset=['rv_next', 'rv_m5'] + PRICE)
    panel = panel[panel['rv_m5'] > 0]
    # 점프 라벨
    panel['jump'] = (panel['rv_next'] >= JUMP_X * panel['rv_m5']).astype(int)
    panel['year'] = panel['day'].dt.year

    # 고밀도 날 한정
    hp = panel[panel['posts'] >= MIN_DAILY_POSTS].copy()
    print(f'[4] 고밀도 패널 {len(hp):,}행 — 점프율 {hp["jump"].mean():.3f} '
          f'(점프배율 {JUMP_X}x)')
    years = sorted(hp['year'].unique())
    test_years = [y for y in years if y >= years[0] + 2]
    print(f'    walk-forward 테스트 연도: {test_years}\n')

    print('[A] 가격 블록만')
    auc_a, ap_a = walk_forward(hp, PRICE, 'price', test_years)
    print('[B] 가격 + 개미지수 블록')
    auc_b, ap_b = walk_forward(hp, PRICE + ANT, 'price+ant', test_years)

    print('=== 결론 (변동성 점프 분류) ===')
    print(f'  가격 단독      AUC={auc_a:.4f}  PR-AUC={ap_a:.4f}')
    print(f'  가격+개미지수  AUC={auc_b:.4f}  PR-AUC={ap_b:.4f}')
    print(f'  개미지수 증분  dAUC={auc_b-auc_a:+.4f}  dPR-AUC={ap_b-ap_a:+.4f}')
    if auc_b - auc_a > 0.01:
        print('  -> 개미지수가 변동성 점프 예측에 증분 기여. 모델 B 핵심 신호로.')
    else:
        print('  -> 증분 미미.')


if __name__ == '__main__':
    main()
