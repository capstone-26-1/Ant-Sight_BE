# coding=utf-8
"""
build_ant_index_incremental.py

Stage 2 (개미지수 집계)의 증분 버전.
원본 build_ant_index_v2.py는 scores_v2를 DROP 후 전체 재계산(9년치)하므로
운영 cron에 부적합. 이 스크립트는 최근 구간만 다시 집계하여 UPSERT 한다.

계산식은 원본과 100% 동일 (MODEL_A_V2_RUBRIC.md 5절):
  greed_raw = mean(stance)/2 * 0.5 + mean(euphoria)/3 * 0.5
  fear_raw  = mean(anxiety)/3 * 0.5 + mean(capitulation)/3 * 0.5
  개미지수  = clamp(50 + 50*(greed_raw - fear_raw), 0, 100)

원본과의 차이:
  - 입력 범위: 전체 → 최근 LOOKBACK_HOURS 시간만 (posts.timestamp 기준)
  - 저장 방식: DROP+CREATE+INSERT → INSERT ... ON DUPLICATE KEY UPDATE
              (테이블 유지, 해당 버킷만 갱신/삽입, 프론트 조회 중에도 안전)

사용:
  python build_ant_index_incremental.py                 # 최근 6시간(기본)
  python build_ant_index_incremental.py --hours 48      # 최근 48시간
  python build_ant_index_incremental.py --since 2026-05-20  # 특정 시점 이후
"""
import sys
import json
import argparse
from datetime import datetime, timedelta
from pathlib import Path

import numpy as np
import pandas as pd
import pymysql

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE = Path(__file__).resolve().parent
CFG  = json.load(open(BASE / 'db_config.json', encoding='utf-8'))

BUCKET = '15min'
DEFAULT_LOOKBACK_HOURS = 6   # 기본 최근 6시간 재집계 (15분 버킷 경계 여유 포함)


def conn():
    return pymysql.connect(host=CFG['host'], port=CFG.get('port', 3306),
                           user=CFG['user'], password=CFG['password'],
                           charset=CFG.get('charset', 'utf8mb4'))


def resolve_cutoff(args) -> str:
    """
    재집계 시작 시점(cutoff)을 'YYYY.MM.DD HH:MM' 형식 문자열로 반환.
    posts.timestamp가 '2026.05.14 17:15' 형식이라 그에 맞춰 비교.
    """
    if args.since:
        # '2026-05-20' 또는 '2026-05-20 13:00' 입력 허용
        s = args.since.replace('-', '.')
        if len(s) == 10:        # 날짜만
            s += ' 00:00'
        return s
    # --hours 기준: 현재(KST 가정) - N시간
    cutoff_dt = datetime.now() - timedelta(hours=args.hours)
    # 15분 버킷 경계로 내림 (버킷 일부만 재계산되는 것 방지)
    minute = (cutoff_dt.minute // 15) * 15
    cutoff_dt = cutoff_dt.replace(minute=minute, second=0, microsecond=0)
    return cutoff_dt.strftime('%Y.%m.%d %H:%M')


def main():
    parser = argparse.ArgumentParser(description='개미지수 증분 갱신 (scores_v2 UPSERT)')
    parser.add_argument('--hours', type=int, default=DEFAULT_LOOKBACK_HOURS,
                        help=f'최근 N시간 재집계 (기본 {DEFAULT_LOOKBACK_HOURS})')
    parser.add_argument('--since', type=str, default=None,
                        help="특정 시점 이후 (예: 2026-05-20 또는 '2026-05-20 13:00')")
    args = parser.parse_args()

    cutoff = resolve_cutoff(args)
    c = conn()

    print(f'[1] posts + post_labels_v2 조인 로딩 (cutoff={cutoff} 이후) ...', flush=True)
    # 원본과 동일한 SELECT, 단 timestamp >= cutoff 조건 추가.
    # posts.timestamp가 'YYYY.MM.DD HH:MM' 문자열이라 문자열 비교가 사전식=시간순이라 성립.
    df = pd.read_sql("""
        SELECT p.stock_code, p.timestamp,
               l.post_type, l.stance, l.euphoria,
               l.anxiety, l.capitulation, l.anger
        FROM stockboard.post_labels_v2 l
        JOIN stockboard.posts p ON p.id = l.post_id
        WHERE p.timestamp IS NOT NULL
          AND p.timestamp >= %s
    """, c, params=[cutoff])
    print(f'    {len(df):,}건 로드')

    if len(df) == 0:
        print('[완료] 재집계 대상 없음. 종료.')
        c.close()
        return

    # ── 이하 계산 로직은 원본 build_ant_index_v2.py와 100% 동일 ──
    df['dt'] = pd.to_datetime(df['timestamp'], format='%Y.%m.%d %H:%M',
                              errors='coerce')
    df = df.dropna(subset=['dt'])
    df['bucket'] = df['dt'].dt.floor(BUCKET)
    print(f'[2] 시간 파싱 후 {len(df):,}건, 버킷 단위 {BUCKET}')

    for col in ('stance', 'euphoria', 'anxiety', 'capitulation', 'anger'):
        df[col] = pd.to_numeric(df[col])

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
    ana = (g['post_type'].apply(lambda s: (s == '분석정보').mean())
           .reset_index(name='analysis_ratio'))
    agg = agg.merge(ana, on=['stock_code', 'bucket'])

    greed = agg['mean_stance'] / 2 * 0.5 + agg['mean_euphoria'] / 3 * 0.5
    fear  = agg['mean_anxiety'] / 3 * 0.5 + agg['mean_capit'] / 3 * 0.5
    agg['greed_raw'] = greed
    agg['fear_raw']  = fear
    agg['ant_index'] = (50 + 50 * (greed - fear)).clip(0, 100)
    agg['std_stance'] = agg['std_stance'].fillna(0.0)

    print(f'[4] 집계 결과 {len(agg):,}개 버킷')
    print(f'    개미지수 분포: min={agg["ant_index"].min():.1f} '
          f'median={agg["ant_index"].median():.1f} '
          f'max={agg["ant_index"].max():.1f}')

    # ── 저장: 원본과 다른 부분. DROP 안 하고 UPSERT ──
    print('[5] ant_index.scores_v2 UPSERT ...', flush=True)
    with c.cursor() as cur:
        # 테이블이 없을 때만 생성 (있으면 그대로 유지)
        cur.execute("CREATE DATABASE IF NOT EXISTS ant_index "
                    "DEFAULT CHARSET=utf8mb4")
        cur.execute("""
            CREATE TABLE IF NOT EXISTS ant_index.scores_v2 (
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
    # PK(stock_code, bucket_time) 충돌 시 나머지 컬럼 갱신
    update_cols = [col for col in cols if col not in ('stock_code', 'bucket_time')]
    update_clause = ', '.join([f"{col}=VALUES({col})" for col in update_cols])
    sql = (f"INSERT INTO ant_index.scores_v2 ({','.join(cols)}) "
           f"VALUES ({ph}) "
           f"ON DUPLICATE KEY UPDATE {update_clause}")

    CH = 5000
    with c.cursor() as cur:
        for i in range(0, len(rows), CH):
            cur.executemany(sql, rows[i:i + CH])
            c.commit()
            print(f'    {min(i+CH, len(rows)):,}/{len(rows):,}', flush=True)
    c.close()
    print(f'[완료] ant_index.scores_v2 — {len(rows):,}개 버킷 UPSERT')


if __name__ == '__main__':
    main()
