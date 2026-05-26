# coding=utf-8
"""
Model B v1 — 실시간 변동성 점프 경보 추론.

저장된 model_b/ 로 종목별 '다음 거래일 변동성 점프 확률' 을 산출.

입력 : model_b/model_b.joblib, meta.json   (train_model_b_final.py 산출)
       stock_data.candles_5min, ant_index.scores_v2
출력 : 콘솔 랭킹 + ant_index.voljump_alert 테이블

대상: 개미지수가 있는 100종목 전체를 채점한다. 학습은 유효 34종목으로만
했지만 모델은 가격·개미 피처만 쓰므로 어떤 종목에든 적용 가능. 각 행을
4가지 상태로 구분:
  VALID    최신 거래일 기준 + 게시글 충분 + 학습 유효종목  → 행동 가능 경보
  LOWCONF  게시글 부족(< MIN_DAILY_POSTS)               → 점수 내되 신뢰 낮음
  OOU      학습 유효종목 밖(삼성전자 등 단기 크롤 종목)   → 참고용
  STALE    기준일이 최신 거래일보다 과거                 → 이미 지난 예측
"""
import sys
import json
import warnings
from pathlib import Path

import numpy as np
import pandas as pd
import pymysql
import joblib

warnings.filterwarnings('ignore')
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE = Path(__file__).resolve().parent
CFG  = json.load(open(BASE / 'db_config.json', encoding='utf-8'))
MDIR = BASE / 'model_b'


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


def main():
    if not (MDIR / 'model_b.joblib').exists():
        print('[오류] model_b/ 없음 — train_model_b_final.py 먼저 실행')
        sys.exit(1)
    model = joblib.load(MDIR / 'model_b.joblib')
    meta  = json.load(open(MDIR / 'meta.json', encoding='utf-8'))
    feats  = meta['features']
    valid34 = set(meta['valid_stocks'])
    min_dp  = meta['filter']['min_daily_posts']
    base    = meta['jump_rate']
    print(f'[1] 모델 로드 — 피처 {len(feats)}개, 학습 유효종목 {len(valid34)}개')

    c = conn()
    cd = pd.read_sql("""
        SELECT ticker AS stock_code, candle_time, open_price, high_price,
               low_price, close_price, volume FROM stock_data.candles_5min WHERE candle_time >= DATE_SUB(NOW(), INTERVAL 60 DAY)
    """, c)
    ant = pd.read_sql("""
        SELECT stock_code, bucket_time, post_count, ant_index, greed_raw,
               fear_raw, mean_anger, std_stance, analysis_ratio
        FROM ant_index.scores_v2 WHERE bucket_time >= '2021-04-01'
    """, c)
    cd['candle_time'] = pd.to_datetime(cd['candle_time'])
    for col in ('open_price', 'high_price', 'low_price', 'close_price'):
        cd[col] = pd.to_numeric(cd[col])
    ant['bucket_time'] = pd.to_datetime(ant['bucket_time'])
    # 개미지수가 있는 종목 전체 채점 (34개 제한 없음)
    universe = set(ant['stock_code']) & set(cd['stock_code'])
    cd  = cd[cd['stock_code'].isin(universe)].copy()
    ant = ant[ant['stock_code'].isin(universe)].copy()
    print(f'[2] 채점 대상 {len(universe)}종목 (개미지수+캔들 보유)')

    d = build_daily_price(cd).sort_values(['stock_code', 'day'])
    gd = d.groupby('stock_code')
    d['rv_lag1']  = gd['rv'].shift(1)
    d['rv_lag2']  = gd['rv'].shift(2)
    d['rv_m5']    = gd['rv'].shift(1).rolling(5).mean().reset_index(0, drop=True)
    d['ret_lag1'] = gd['ret'].shift(1)
    d['abs_lag1'] = gd['absret'].shift(1)
    d['hl_lag1']  = gd['hl'].shift(1)
    d['vol_lag1'] = gd['logvol'].shift(1)
    market_last = d['day'].max()

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
    a['log_posts'] = np.log1p(a['posts'])
    ga = a.groupby('stock_code')
    a['d_ant']     = a['ant']   - ga['ant'].shift(1)
    a['d_anger']   = a['anger'] - ga['anger'].shift(1)
    a['post_surge'] = a['log_posts'] - ga['log_posts'].transform(
        lambda x: x.shift(1).rolling(20, min_periods=5).median())
    a['disp_surge'] = a['disp'] - ga['disp'].transform(
        lambda x: x.shift(1).rolling(20, min_periods=5).median())

    panel = d.merge(a, on=['stock_code', 'day'], how='left')
    # 게시글 필터 없음 — posts<min_dp 도 채점하되 LOWCONF 로 표기
    panel = panel.dropna(subset=['rv_m5'] + feats)
    panel = panel[panel['rv_m5'] > 0]

    latest = panel.sort_values('day').groupby('stock_code').tail(1).copy()
    latest['jump_prob'] = model.predict_proba(latest[feats])[:, 1]

    def status(r):
        if r.day < market_last:
            return 'STALE'
        if r.stock_code not in valid34:
            return 'OOU'
        if r.posts < min_dp:
            return 'LOWCONF'
        return 'VALID'
    latest['status'] = [status(r) for r in latest.itertuples(index=False)]
    latest = latest.sort_values(['status', 'jump_prob'],
                                ascending=[True, False])

    order = {'VALID': 0, 'LOWCONF': 1, 'OOU': 2, 'STALE': 3}
    latest['_o'] = latest['status'].map(order)
    latest = latest.sort_values(['_o', 'jump_prob'], ascending=[True, False])

    print(f'\n[3] 변동성 점프 경보  (최신 거래일 {market_last.date()}, '
          f'기준 점프율 {base:.1%})')
    print(f'    {"종목":>8} {"기준일":>12} {"점프확률":>9} {"posts":>7} '
          f'{"여론분산":>9} {"상태":>9} {"경보":>6}')
    cur_st = None
    for r in latest.itertuples(index=False):
        if r.status != cur_st:
            cur_st = r.status
            tag = {'VALID': '── 행동 가능 경보 ──',
                   'LOWCONF': '── 게시글 부족(신뢰 낮음) ──',
                   'OOU': '── 학습 범위 밖(참고용) ──',
                   'STALE': '── 기준일 경과(지난 예측) ──'}[cur_st]
            print(f'  {tag}')
        flag = '★HIGH' if r.jump_prob >= 2 * base else (
               '▲' if r.jump_prob >= base else '')
        print(f'    {r.stock_code:>8} {str(r.day.date()):>12} '
              f'{r.jump_prob:>8.1%} {int(r.posts):>7,} '
              f'{r.disp:>9.3f} {r.status:>9} {flag:>6}')

    with c.cursor() as cur:
        cur.execute("""
            CREATE TABLE IF NOT EXISTS ant_index.voljump_alert (
                stock_code  VARCHAR(10),
                base_day    DATE,
                jump_prob   DECIMAL(6,4),
                posts       INT,
                disp        DECIMAL(6,4),
                status      VARCHAR(10),
                scored_at   DATETIME,
                PRIMARY KEY (stock_code, base_day)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)
        # status 컬럼이 없던 기존 테이블 대비
        cur.execute("SHOW COLUMNS FROM ant_index.voljump_alert LIKE 'status'")
        if not cur.fetchone():
            cur.execute("ALTER TABLE ant_index.voljump_alert "
                        "ADD COLUMN status VARCHAR(10)")
        now = pd.Timestamp.now().to_pydatetime()
        cur.executemany(
            "REPLACE INTO ant_index.voljump_alert "
            "(stock_code,base_day,jump_prob,posts,disp,status,scored_at) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s)",
            [(r.stock_code, r.day.date(), round(float(r.jump_prob), 4),
              int(r.posts), round(float(r.disp), 4), r.status, now)
             for r in latest.itertuples(index=False)])
    c.commit()
    c.close()
    nv = (latest['status'] == 'VALID').sum()
    print(f'\n[완료] voljump_alert 적재 — {len(latest)}종목 '
          f'(VALID 경보 {nv}종목)')


if __name__ == '__main__':
    main()
