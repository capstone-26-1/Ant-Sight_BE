# coding=utf-8
"""
Model A v2 — 7단계: 합성 개미지수(0~100) 집계.

입력 : stockboard.post_labels_v2  (6단계 산출, post 별 6개 필드)
       stockboard.posts           (stock_code, timestamp)
출력 : ant_index.scores_v2        ((종목, 15분 버킷) 별 개미지수)

산출식 (MODEL_A_V2_RUBRIC.md 5절):
  greed_raw = mean(stance)/2 * 0.5 + mean(euphoria)/3 * 0.5
  fear_raw  = mean(anxiety)/3 * 0.5 + mean(capitulation)/3 * 0.5
  개미지수  = clamp( 50 + 50*(greed_raw - fear_raw), 0, 100 )
              0 극공포 .. 50 중립 .. 100 극탐욕

부가 성분: post_count / 분석정보 비중 / anger 평균 / 여론분산(stance 표준편차).
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

BUCKET = '15min'


def conn():
    return pymysql.connect(host=CFG['host'], port=CFG.get('port', 3306),
                           user=CFG['user'], password=CFG['password'],
                           charset=CFG.get('charset', 'utf8mb4'))


def main():
    c = conn()
    print('[1] posts + post_labels_v2 조인 로딩 ...', flush=True)
    df = pd.read_sql("""
        SELECT p.stock_code, p.timestamp,
               l.post_type, l.stance, l.euphoria,
               l.anxiety, l.capitulation, l.anger
        FROM stockboard.post_labels_v2 l
        JOIN stockboard.posts p ON p.id = l.post_id
        WHERE p.timestamp IS NOT NULL
    """, c)
    print(f'    {len(df):,}건 로드')

    # 시간 파싱 + 15분 버킷
    df['dt'] = pd.to_datetime(df['timestamp'], format='%Y.%m.%d %H:%M',
                              errors='coerce')
    df = df.dropna(subset=['dt'])
    df['bucket'] = df['dt'].dt.floor(BUCKET)
    print(f'[2] 시간 파싱 후 {len(df):,}건, 버킷 단위 {BUCKET}')

    for col in ('stance', 'euphoria', 'anxiety', 'capitulation', 'anger'):
        df[col] = pd.to_numeric(df[col])

    # (종목, 버킷) 집계
    print('[3] (종목, 버킷) 집계 ...', flush=True)
    g = df.groupby(['stock_code', 'bucket'])
    agg = g.agg(
        post_count   =('stance', 'size'),
        mean_stance  =('stance', 'mean'),
        std_stance   =('stance', 'std'),
        mean_euphoria=('euphoria', 'mean'),
        mean_anxiety =('anxiety', 'mean'),
        mean_capit   =('capitulation', 'mean'),
        mean_anger   =('anger', 'mean'),
    ).reset_index()
    # 분석정보 비중
    ana = (g['post_type'].apply(lambda s: (s == '분석정보').mean())
           .reset_index(name='analysis_ratio'))
    agg = agg.merge(ana, on=['stock_code', 'bucket'])

    # 개미지수
    greed = agg['mean_stance'] / 2 * 0.5 + agg['mean_euphoria'] / 3 * 0.5
    fear  = agg['mean_anxiety'] / 3 * 0.5 + agg['mean_capit'] / 3 * 0.5
    agg['greed_raw'] = greed
    agg['fear_raw']  = fear
    agg['ant_index'] = (50 + 50 * (greed - fear)).clip(0, 100)
    agg['std_stance'] = agg['std_stance'].fillna(0.0)

    print(f'[4] 집계 결과 {len(agg):,}개 버킷')
    print(f'    개미지수 분포: min={agg["ant_index"].min():.1f} '
          f'p25={agg["ant_index"].quantile(.25):.1f} '
          f'median={agg["ant_index"].median():.1f} '
          f'p75={agg["ant_index"].quantile(.75):.1f} '
          f'max={agg["ant_index"].max():.1f}')
    print(f'    버킷당 게시글 수: median={agg["post_count"].median():.0f} '
          f'mean={agg["post_count"].mean():.1f}')

    # 테이블 생성 + 적재
    print('[5] ant_index.scores_v2 적재 ...', flush=True)
    with c.cursor() as cur:
        cur.execute("CREATE DATABASE IF NOT EXISTS ant_index "
                    "DEFAULT CHARSET=utf8mb4")
        cur.execute("DROP TABLE IF EXISTS ant_index.scores_v2")
        cur.execute("""
            CREATE TABLE ant_index.scores_v2 (
                stock_code     VARCHAR(10),
                bucket_time    DATETIME,
                post_count     INT,
                ant_index      DECIMAL(5,2),
                greed_raw      DECIMAL(6,4),
                fear_raw       DECIMAL(6,4),
                mean_stance    DECIMAL(6,4),
                std_stance     DECIMAL(6,4),
                mean_euphoria  DECIMAL(6,4),
                mean_anxiety   DECIMAL(6,4),
                mean_capit     DECIMAL(6,4),
                mean_anger     DECIMAL(6,4),
                analysis_ratio DECIMAL(6,4),
                PRIMARY KEY (stock_code, bucket_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)
        c.commit()

    cols = ['stock_code', 'bucket_time', 'post_count', 'ant_index',
            'greed_raw', 'fear_raw', 'mean_stance', 'std_stance',
            'mean_euphoria', 'mean_anxiety', 'mean_capit', 'mean_anger',
            'analysis_ratio']
    rows = [(
        r.stock_code, r.bucket.to_pydatetime(), int(r.post_count),
        round(float(r.ant_index), 2), round(float(r.greed_raw), 4),
        round(float(r.fear_raw), 4), round(float(r.mean_stance), 4),
        round(float(r.std_stance), 4), round(float(r.mean_euphoria), 4),
        round(float(r.mean_anxiety), 4), round(float(r.mean_capit), 4),
        round(float(r.mean_anger), 4), round(float(r.analysis_ratio), 4),
    ) for r in agg.itertuples(index=False)]

    ph = ','.join(['%s'] * len(cols))
    sql = f"INSERT INTO ant_index.scores_v2 ({','.join(cols)}) VALUES ({ph})"
    CH = 5000
    with c.cursor() as cur:
        for i in range(0, len(rows), CH):
            cur.executemany(sql, rows[i:i + CH])
            c.commit()
            print(f'    {min(i+CH, len(rows)):,}/{len(rows):,}', flush=True)
    c.close()
    print(f'[완료] ant_index.scores_v2 — {len(rows):,}개 버킷 적재')


if __name__ == '__main__':
    main()
