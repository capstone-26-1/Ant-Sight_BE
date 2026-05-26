# coding=utf-8
"""
시장 개미지수 정규화 — CNN 공탐지수式 0~100 게이지.

원지수(ant_index.market_index)는 100종목 평균이라 41~54로 압축돼 있어
가독성이 낮음. 이를 *과거 1년 분포 대비 백분위*로 변환해 0~100 전 구간을
또렷이 쓰게 한다. "오늘은 지난 1년 중 하위 X% = 공포" 형태.

look-ahead 없음: 각 날의 백분위는 직전 WINDOW 일 데이터로만 계산.
신뢰구간: n_stocks 가 충분한(>=MIN_STOCKS) 날만 사용.

출력: ant_index.market_index_norm 테이블 + market_antindex_norm.csv
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
OUT_CSV = BASE / 'market_antindex_norm.csv'

WINDOW     = 365     # 백분위 산출 trailing window (일 단위 행)
MIN_PERIOD = 120     # 최소 누적일 (그 전엔 NaN)
MIN_STOCKS = 20      # 신뢰구간 필터


def conn():
    return pymysql.connect(host=CFG['host'], port=CFG.get('port', 3306),
                           user=CFG['user'], password=CFG['password'],
                           charset=CFG.get('charset', 'utf8mb4'))


def norm_zone(p):
    if p < 10:   return '극공포'
    if p < 35:   return '공포'
    if p < 65:   return '중립'
    if p < 90:   return '탐욕'
    return '극탐욕'


def main():
    print('[1] 원 시장지수 로딩 ...', flush=True)
    c = conn()
    m = pd.read_sql("""
        SELECT date, market_ant_index, total_posts, n_stocks
        FROM ant_index.market_index ORDER BY date
    """, c)
    m['date'] = pd.to_datetime(m['date'])
    m['market_ant_index'] = pd.to_numeric(m['market_ant_index'])

    # 신뢰구간만
    m = m[m['n_stocks'] >= MIN_STOCKS].sort_values('date').reset_index(drop=True)
    print(f'    신뢰구간(n_stocks>={MIN_STOCKS}) {len(m):,}일  '
          f'({m["date"].min().date()} ~ {m["date"].max().date()})')

    # trailing 백분위
    print('[2] trailing 백분위 정규화 ...', flush=True)
    def pct_rank(x):
        return (x <= x[-1]).mean() * 100
    m['norm_index'] = (m['market_ant_index']
                       .rolling(WINDOW, min_periods=MIN_PERIOD)
                       .apply(pct_rank, raw=True))
    m = m.dropna(subset=['norm_index']).reset_index(drop=True)
    m['norm_zone'] = m['norm_index'].apply(norm_zone)
    print(f'    정규화 완료 {len(m):,}일  '
          f'({m["date"].min().date()} ~ {m["date"].max().date()})')

    # 적재
    print('[3] ant_index.market_index_norm 적재 ...', flush=True)
    with c.cursor() as cur:
        cur.execute("DROP TABLE IF EXISTS ant_index.market_index_norm")
        cur.execute("""
            CREATE TABLE ant_index.market_index_norm (
                date         DATE PRIMARY KEY,
                raw_index    DECIMAL(5,2),
                norm_index   DECIMAL(5,2),
                norm_zone    VARCHAR(8),
                total_posts  INT,
                n_stocks     INT
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)
        c.commit()
        cur.executemany(
            "INSERT INTO ant_index.market_index_norm "
            "(date,raw_index,norm_index,norm_zone,total_posts,n_stocks) "
            "VALUES (%s,%s,%s,%s,%s,%s)",
            [(r.date.date(), round(float(r.market_ant_index), 2),
              round(float(r.norm_index), 1), r.norm_zone,
              int(r.total_posts), int(r.n_stocks))
             for r in m.itertuples(index=False)])
    c.commit()
    c.close()
    m.to_csv(OUT_CSV, index=False, encoding='utf-8-sig')

    print(f'\n[4] 정규화 지수 구간 분포')
    for z in ['극공포', '공포', '중립', '탐욕', '극탐욕']:
        n = (m['norm_zone'] == z).sum()
        print(f'    {z:<5} {n:>5}일 ({100*n/len(m):4.1f}%)')

    print(f'\n[5] 최근 흐름 — 원지수 vs 정규화')
    recent = m[m['date'] >= '2026-05-08']
    print(f'    {"날짜":>12} {"원지수":>8} {"정규화":>8} {"구간":>7} '
          f'{"게시글":>9}')
    for r in recent.itertuples(index=False):
        print(f'    {str(r.date.date()):>12} {r.market_ant_index:>8.1f} '
              f'{r.norm_index:>8.1f} {r.norm_zone:>7} {r.total_posts:>9,}')

    print(f'\n[완료] ant_index.market_index_norm ({len(m):,}일) + '
          f'{OUT_CSV.name}')


if __name__ == '__main__':
    main()
