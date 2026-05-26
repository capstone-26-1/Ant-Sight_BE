# coding=utf-8
"""
시장 단위 개미지수 — 한국 리테일 공포탐욕지수.

종목별 개미지수(ant_index.scores_v2, 15분 버킷)를 전체 종목 집계해 매일
하나의 0~100 숫자로. CNN 공포탐욕지수와 같은 형태의 시장 심리 게이지.

집계: 각 날짜에 대해 모든 종목-버킷을 post_count 가중 평균.
출력: ant_index.market_index 테이블 + market_antindex.csv

용도: 예측 알파가 아니라 — 시장 군중심리 상태 게이지, 행동 규율,
      극단 구간 인지. (공탐지수가 쓰이는 방식 그대로.)
"""
import sys
import json
from pathlib import Path

import numpy as np
import pandas as pd
import pymysql

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE = Path(__file__).resolve().parent
CFG  = json.load(open(BASE / 'db_config.json', encoding='utf-8'))
OUT_CSV = BASE / 'market_antindex.csv'


def conn():
    return pymysql.connect(host=CFG['host'], port=CFG.get('port', 3306),
                           user=CFG['user'], password=CFG['password'],
                           charset=CFG.get('charset', 'utf8mb4'))


def zone(v):
    if v < 25:   return '극공포'
    if v < 45:   return '공포'
    if v < 55:   return '중립'
    if v < 75:   return '탐욕'
    return '극탐욕'


def main():
    print('[1] 종목별 개미지수 로딩 ...', flush=True)
    c = conn()
    sv = pd.read_sql("""
        SELECT stock_code, bucket_time, post_count, ant_index,
               greed_raw, fear_raw, mean_euphoria, mean_anxiety,
               mean_capit, mean_anger, analysis_ratio
        FROM ant_index.scores_v2
    """, c)
    sv['bucket_time'] = pd.to_datetime(sv['bucket_time'])
    sv['date'] = sv['bucket_time'].dt.floor('D')
    for col in ('ant_index', 'greed_raw', 'fear_raw', 'mean_euphoria',
                'mean_anxiety', 'mean_capit', 'mean_anger', 'analysis_ratio'):
        sv[col] = pd.to_numeric(sv[col])
    print(f'    버킷 {len(sv):,}개')

    print('[2] 일별 시장 집계 (post_count 가중) ...', flush=True)
    rows = []
    for dt, g in sv.groupby('date'):
        w = g['post_count'].astype(float)
        sw = w.sum()
        rows.append((
            dt, int(sw), int(g['stock_code'].nunique()),
            np.average(g['ant_index'], weights=w),
            np.average(g['greed_raw'], weights=w),
            np.average(g['fear_raw'], weights=w),
            np.average(g['mean_euphoria'], weights=w),
            np.average(g['mean_anxiety'], weights=w),
            np.average(g['mean_capit'], weights=w),
            np.average(g['mean_anger'], weights=w),
            np.average(g['analysis_ratio'], weights=w),
            # 종목간 의견 분산 (개미지수 표준편차)
            g['ant_index'].std(),
        ))
    m = pd.DataFrame(rows, columns=[
        'date', 'total_posts', 'n_stocks', 'market_ant_index',
        'greed', 'fear', 'euphoria', 'anxiety', 'capitulation',
        'anger', 'analysis_ratio', 'cross_dispersion'])
    m = m.sort_values('date').reset_index(drop=True)
    m['zone'] = m['market_ant_index'].apply(zone)
    print(f'    {len(m):,}일  ({m["date"].min().date()} ~ '
          f'{m["date"].max().date()})')

    # ── 테이블 적재 ──
    print('[3] ant_index.market_index 적재 ...', flush=True)
    with c.cursor() as cur:
        cur.execute("DROP TABLE IF EXISTS ant_index.market_index")
        cur.execute("""
            CREATE TABLE ant_index.market_index (
                date             DATE PRIMARY KEY,
                market_ant_index DECIMAL(5,2),
                zone             VARCHAR(8),
                total_posts      INT,
                n_stocks         INT,
                greed            DECIMAL(6,4),
                fear             DECIMAL(6,4),
                euphoria         DECIMAL(6,4),
                anxiety          DECIMAL(6,4),
                capitulation     DECIMAL(6,4),
                anger            DECIMAL(6,4),
                analysis_ratio   DECIMAL(6,4),
                cross_dispersion DECIMAL(6,4)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)
        c.commit()
        cur.executemany(
            "INSERT INTO ant_index.market_index (date,market_ant_index,zone,"
            "total_posts,n_stocks,greed,fear,euphoria,anxiety,capitulation,"
            "anger,analysis_ratio,cross_dispersion) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            [(r.date.date(), round(float(r.market_ant_index), 2), r.zone,
              int(r.total_posts), int(r.n_stocks),
              round(float(r.greed), 4), round(float(r.fear), 4),
              round(float(r.euphoria), 4), round(float(r.anxiety), 4),
              round(float(r.capitulation), 4), round(float(r.anger), 4),
              round(float(r.analysis_ratio), 4),
              round(float(r.cross_dispersion), 4) if np.isfinite(
                  r.cross_dispersion) else None)
             for r in m.itertuples(index=False)])
    c.commit()
    c.close()
    m.to_csv(OUT_CSV, index=False, encoding='utf-8-sig')

    # ── 점검 ──
    print(f'\n[4] 시장 개미지수 분포')
    v = m['market_ant_index']
    print(f'    평균 {v.mean():.1f}  최소 {v.min():.1f}  최대 {v.max():.1f}')
    for p in (10, 25, 50, 75, 90):
        print(f'    p{p}: {v.quantile(p/100):.1f}')
    print(f'    구간 분포:')
    for z in ['극공포', '공포', '중립', '탐욕', '극탐욕']:
        n = (m['zone'] == z).sum()
        print(f'      {z:<5} {n:>5}일 ({100*n/len(m):4.1f}%)')

    print(f'\n[5] 최근 흐름 (2026-05, 게시글 충분한 구간)')
    recent = m[m['date'] >= '2026-05-08']
    print(f'    {"날짜":>12} {"개미지수":>9} {"구간":>7} {"게시글":>9} '
          f'{"종목":>5}')
    for r in recent.itertuples(index=False):
        print(f'    {str(r.date.date()):>12} {r.market_ant_index:>9.1f} '
              f'{r.zone:>7} {r.total_posts:>9,} {r.n_stocks:>5}')

    print(f'\n[완료] ant_index.market_index ({len(m):,}일) + '
          f'{OUT_CSV.name} 저장')


if __name__ == '__main__':
    main()
