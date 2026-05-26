# coding=utf-8
"""
Model B 추가 검증 — 시계열·구조·경제적 가치.

train_model_b_voljump.py 의 walk-forward 구조 그대로 OOS 예측을 누적해
다음 8가지를 평가한다:

  [1] 헤드라인 — AUC, PR-AUC
  [2] 캘리브레이션 — Brier score + 예측확률 10구간 reliability
  [3] 리프트 — 예측확률 10분위별 실제 점프율
  [4] 임계값 스윕 — precision/recall/F1 at 0.3/0.4/0.5
  [5] 시계열 안정성 — 월별 롤링 AUC (시간 흐름에 따라 유지되는가)
  [6] 종목 일관성 — 종목별 AUC 분포
  [7] 국면 의존성 — 그 종목의 rv_m5 5분위별 AUC
  [8] 경제적 가치 — 경보 시 비중 축소 룰의 Sharpe·MDD 개선

블록: 가격 단독 vs 가격+개미. 모든 평가를 두 블록 동일 절차로 비교.
"""
import sys
import json
import warnings
from pathlib import Path

import numpy as np
import pandas as pd
import pymysql
import lightgbm as lgb
from sklearn.metrics import (roc_auc_score, average_precision_score,
                             brier_score_loss, precision_recall_fscore_support)

warnings.filterwarnings('ignore')
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE = Path(__file__).resolve().parent
CFG  = json.load(open(BASE / 'db_config.json', encoding='utf-8'))

MIN_DAYS, MIN_PPD, MIN_DAILY_POSTS = 500, 12, 10
JUMP_X = 1.5


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
    d = g.agg(rv=('lret', lambda x: x.std()),
              day_o=('open_price', 'first'), day_c=('close_price', 'last'),
              day_h=('high_price', 'max'), day_l=('low_price', 'min'),
              volume=('volume', 'sum'), n_bar=('lret', 'size')).reset_index()
    d = d[d['n_bar'] >= 10].copy()
    d['ret']    = d['day_c'] / d['day_o'] - 1.0
    d['absret'] = d['ret'].abs()
    d['hl']     = (d['day_h'] - d['day_l']) / d['day_o']
    d['logvol'] = np.log1p(d['volume'].astype(float))
    return d


def walk_forward_predict(panel, feats, test_years):
    """각 test 연도에 대해 그 직전까지로 학습→예측. 누적 OOS DataFrame 반환."""
    out = []
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
        prob = m.predict_proba(te[feats])[:, 1]
        out.append(pd.DataFrame({
            'stock_code': te['stock_code'].values,
            'day':        te['day'].values,
            'year':       ty,
            'jump':       te['jump'].values,
            'rv_m5':      te['rv_m5'].values,
            'ret_next':   te['ret_next'].values,
            'prob':       prob,
        }))
    return pd.concat(out, ignore_index=True)


def evaluate(oos, label):
    y, p = oos['jump'].values, oos['prob'].values
    print(f'\n{"=" * 64}\n[{label}]  OOS n={len(oos):,}, 점프율 {y.mean():.3f}\n'
          f'{"=" * 64}')

    # [1] 헤드라인
    auc = roc_auc_score(y, p); ap = average_precision_score(y, p)
    print(f'[1] AUC={auc:.4f}  PR-AUC={ap:.4f}')

    # [2] 캘리브레이션
    bs = brier_score_loss(y, p)
    print(f'[2] Brier score={bs:.4f}  (낮을수록 잘 보정됨)')
    print('    Reliability — 예측확률 구간별 실제 점프율:')
    bins = np.linspace(0, 1, 11)
    for i in range(10):
        m = (p >= bins[i]) & (p < bins[i+1])
        if m.sum() >= 30:
            print(f'    [{bins[i]:.1f}~{bins[i+1]:.1f}) n={m.sum():>6,}  '
                  f'평균예측 {p[m].mean():.3f}  실제점프율 {y[m].mean():.3f}')

    # [3] 리프트
    print('[3] 리프트 — 예측확률 10분위별 실제 점프율 (Q10=가장 위험)')
    q = pd.qcut(p, 10, labels=range(10), duplicates='drop')
    for d in sorted(pd.unique(q)):
        m = (q == d)
        if m.sum():
            print(f'    Q{int(d)+1:02d}  n={m.sum():>5,}  실제점프율 {y[m].mean():.3f}  '
                  f'(baseline {y.mean():.3f}, lift ×{y[m].mean()/y.mean():.2f})')

    # [4] 임계값 스윕
    print('[4] 임계값 스윕 (precision/recall/F1)')
    for th in (0.20, 0.30, 0.40, 0.50):
        yp = (p >= th).astype(int)
        if yp.sum() == 0:
            continue
        pr, rc, f1, _ = precision_recall_fscore_support(
            y, yp, average='binary', zero_division=0)
        print(f'    th={th:.2f}  경보율 {yp.mean():.3f}  '
              f'precision {pr:.3f}  recall {rc:.3f}  F1 {f1:.3f}')

    # [5] 시계열 안정성
    print('[5] 시계열 안정성 — 월별 AUC')
    oos2 = oos.copy()
    oos2['ym'] = pd.to_datetime(oos2['day']).dt.to_period('M')
    aucs_m = []
    for ym, g in oos2.groupby('ym'):
        if g['jump'].nunique() < 2 or len(g) < 50:
            continue
        aucs_m.append((str(ym), roc_auc_score(g['jump'], g['prob']), len(g)))
    if aucs_m:
        a = np.array([x[1] for x in aucs_m])
        print(f'    월 수 {len(aucs_m)}  평균 AUC {a.mean():.4f}  '
              f'표준편차 {a.std():.4f}  AUC>0.55 월비율 {100*(a>0.55).mean():.0f}%')
        print(f'    최악 3개월: ' + '  '.join(
            f'{m}({v:.3f})' for m, v, _ in sorted(aucs_m, key=lambda x: x[1])[:3]))
        print(f'    최고 3개월: ' + '  '.join(
            f'{m}({v:.3f})' for m, v, _ in sorted(aucs_m, key=lambda x: -x[1])[:3]))

    # [6] 종목 일관성
    print('[6] 종목별 AUC 분포')
    aucs_s = []
    for sc, g in oos.groupby('stock_code'):
        if g['jump'].nunique() >= 2 and len(g) >= 100:
            aucs_s.append(roc_auc_score(g['jump'], g['prob']))
    a = np.array(aucs_s)
    print(f'    종목 {len(a)}개  중앙값 {np.median(a):.3f}  '
          f'25/75% [{np.percentile(a, 25):.3f}, {np.percentile(a, 75):.3f}]  '
          f'AUC>0.55 종목비율 {100*(a>0.55).mean():.0f}%')

    # [7] 국면 의존성 — 그 종목의 rv_m5 5분위
    print('[7] 국면 의존성 — rv_m5(최근5일 평균변동성) 5분위별 AUC')
    oos['rv_q'] = oos.groupby('stock_code')['rv_m5'].transform(
        lambda x: pd.qcut(x, 5, labels=range(5), duplicates='drop'))
    for q in range(5):
        g = oos[oos['rv_q'] == q]
        if g['jump'].nunique() < 2 or len(g) < 200:
            continue
        a = roc_auc_score(g['jump'], g['prob'])
        print(f'    Q{q+1} (변동성 {"저" if q==0 else "고" if q==4 else "중"})  '
              f'n={len(g):>6,}  점프율 {g["jump"].mean():.3f}  AUC {a:.4f}')

    # [8] 경제적 가치 — 경보 시 비중 축소
    print('[8] 경제적 가치 — 경보 시 비중 축소(prob≥0.4 → 절반) 룰')
    e = oos.dropna(subset=['ret_next']).copy()
    e['w']  = np.where(e['prob'] >= 0.40, 0.5, 1.0)
    e['day'] = pd.to_datetime(e['day'])
    daily = e.groupby('day').apply(
        lambda g: pd.Series({
            'bh':   g['ret_next'].mean(),
            'strat':(g['w'] * g['ret_next']).sum() / g['w'].sum(),
        }))
    for name, r in [('B&H(전체노출)', daily['bh']),
                    ('경보축소 룰',     daily['strat'])]:
        cum = (1 + r).cumprod()
        yrs = len(r) / 252
        cagr = cum.iloc[-1] ** (1/yrs) - 1
        sh = r.mean() / r.std() * np.sqrt(252)
        mdd = (cum / cum.cummax() - 1).min()
        print(f'    {name:<14} CAGR {100*cagr:+6.1f}%  Sharpe {sh:.2f}  '
              f'MDD {100*mdd:.1f}%')
    diff = daily['strat'] - daily['bh']
    print(f'    초과수익 t-stat {diff.mean()/diff.std()*np.sqrt(len(diff)):+.2f}'
          f'   (양이면 경보 룰이 통계적으로 더 나음)')


def main():
    c = conn()
    print('[0] 유효종목 + 데이터 로딩 ...', flush=True)
    stat = pd.read_sql("""
        SELECT stock_code, COUNT(DISTINCT DATE(bucket_time)) AS ad,
               SUM(post_count)/COUNT(DISTINCT DATE(bucket_time)) AS ppd
        FROM ant_index.scores_v2 WHERE bucket_time >= '2021-04-01'
        GROUP BY stock_code
    """, c)
    valid = stat[(stat['ad'] >= MIN_DAYS) &
                 (stat['ppd'] >= MIN_PPD)]['stock_code'].tolist()

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

    d = build_daily_price(cd).sort_values(['stock_code', 'day'])
    gd = d.groupby('stock_code')
    d['rv_lag1'], d['rv_lag2'] = gd['rv'].shift(1), gd['rv'].shift(2)
    d['rv_m5']    = gd['rv'].shift(1).rolling(5).mean().reset_index(0, drop=True)
    d['ret_lag1'], d['abs_lag1'] = gd['ret'].shift(1), gd['absret'].shift(1)
    d['hl_lag1'], d['vol_lag1']  = gd['hl'].shift(1), gd['logvol'].shift(1)
    d['rv_next']  = gd['rv'].shift(-1)
    d['ret_next'] = gd['ret'].shift(-1)
    PRICE = ['rv_lag1', 'rv_lag2', 'rv_m5', 'ret_lag1',
             'abs_lag1', 'hl_lag1', 'vol_lag1']

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
    ga = a.sort_values(['stock_code', 'day']).groupby('stock_code')
    a['d_ant']     = a['ant']   - ga['ant'].shift(1)
    a['d_anger']   = a['anger'] - ga['anger'].shift(1)
    a['log_posts'] = np.log1p(a['posts'])
    ANT = ['ant', 'greed', 'fear', 'anger', 'disp', 'ana',
           'log_posts', 'd_ant', 'd_anger']

    panel = d.merge(a, on=['stock_code', 'day'], how='left')
    panel = panel.dropna(subset=['rv_next', 'rv_m5'] + PRICE)
    panel = panel[panel['rv_m5'] > 0]
    panel['jump'] = (panel['rv_next'] >= JUMP_X * panel['rv_m5']).astype(int)
    panel['year'] = panel['day'].dt.year
    hp = panel[panel['posts'] >= MIN_DAILY_POSTS].copy()
    years = sorted(hp['year'].unique())
    test_years = [y for y in years if y >= years[0] + 2]
    print(f'    고밀도 패널 {len(hp):,}행, 점프율 {hp["jump"].mean():.3f}, '
          f'테스트연도 {test_years}\n')

    print('  walk-forward 예측 — 가격 단독 ...', flush=True)
    oos_p = walk_forward_predict(hp, PRICE, test_years)
    print('  walk-forward 예측 — 가격+개미 ...', flush=True)
    oos_b = walk_forward_predict(hp, PRICE + ANT, test_years)

    evaluate(oos_p, '가격 단독')
    evaluate(oos_b, '가격 + 개미지수')

    # 종합 비교
    print('\n' + '#' * 64)
    print('# 종합 비교 (헤드라인 차이)')
    print('#' * 64)
    for name, oos in [('가격', oos_p), ('가격+개미', oos_b)]:
        auc = roc_auc_score(oos['jump'], oos['prob'])
        ap  = average_precision_score(oos['jump'], oos['prob'])
        bs  = brier_score_loss(oos['jump'], oos['prob'])
        print(f'  {name:<10} AUC {auc:.4f}  PR-AUC {ap:.4f}  Brier {bs:.4f}')


if __name__ == '__main__':
    main()
