"""
storage.py - 크롤링 데이터를 RDS에 직접 INSERT.

DataManager 클래스로 검증, 트랜잭션, 로깅을 캡슐화.
이전 버전의 save_to_api / try_post_with_retry / isolate_and_save 대체.
"""
from __future__ import annotations

import logging
import os
from contextlib import contextmanager
from typing import Dict, List, Optional

import pymysql
from pymysql.cursors import DictCursor

logger = logging.getLogger(__name__)


class DataManager:
    """
    posts 테이블 INSERT 전담 매니저.

    - 환경변수에서 DB 접속 정보 읽음 (DB_HOST, DB_USER, DB_PASSWORD, DB_NAME)
    - 각 글마다 try/except로 unique 위반 = skip
    - bulk 결과 카운트 반환 (이전 save_to_api와 동일 시그니처)
    """

    def __init__(
        self,
        host: Optional[str] = None,
        user: Optional[str] = None,
        password: Optional[str] = None,
        db: Optional[str] = None,
        port: int = 3306,
    ):
        self.config = {
            "host":     host     or os.environ["DB_HOST"],
            "user":     user     or os.environ["DB_USER"],
            "password": password or os.environ["DB_PASSWORD"],
            "db":       db       or os.environ.get("DB_NAME", "stockboard"),
            "port":     port,
            "charset":  "utf8mb4",
            "cursorclass": DictCursor,
        }

    @contextmanager
    def _connection(self):
        """pymysql 연결을 컨텍스트 매니저로 감쌈. 종료 시 자동 close."""
        conn = pymysql.connect(**self.config)
        try:
            yield conn
        finally:
            conn.close()

    def save_posts(self, posts: List[Dict]) -> Dict[str, int]:
        """
        벌크 저장. 한 건씩 독립 트랜잭션으로 처리.
        중복(unique 위반)은 skip 카운트.

        Returns:
            {"total": N, "inserted": M, "skipped": K, "failed": J}
        """
        if not posts:
            return {"total": 0, "inserted": 0, "skipped": 0, "failed": 0}

        inserted = 0
        skipped = 0
        failed = 0

        for post in posts:
            try:
                if self._save_one(post):
                    inserted += 1
                else:
                    skipped += 1
            except Exception as e:
                logger.error(f"INSERT 실패: {e} | post={post.get('title', '?')[:30]}")
                failed += 1

        logger.info(
            f"✅ DB 저장 완료: 총 {len(posts)}건 / "
            f"inserted {inserted}건 / skipped(중복) {skipped}건 / failed {failed}건"
        )

        return {
            "total":    len(posts),
            "inserted": inserted,
            "skipped":  skipped,
            "failed":   failed,
        }

    def _save_one(self, post: Dict) -> bool:
        """
        한 건 저장. 중복이면 False, 성공이면 True.
        다른 예외는 호출자에게 raise.
        """
        sql = """
            INSERT INTO posts
              (stock_code, title, writer, timestamp, text,
               views, likes, dislikes, comments)
            VALUES
              (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        params = (
            post.get("stock_code"),
            post.get("title"),
            post.get("writer", "unknown"),
            post.get("timestamp"),
            post.get("text", ""),
            int(post.get("views", 0)),
            int(post.get("likes", 0)),
            int(post.get("dislikes", 0)),
            int(post.get("comments", 0)),
        )

        with self._connection() as conn:
            try:
                with conn.cursor() as cursor:
                    cursor.execute(sql, params)
                conn.commit()
                return True
            except pymysql.err.IntegrityError as e:
                # 1062 = Duplicate entry. 그 외 무결성 위반은 다시 raise.
                if e.args and e.args[0] == 1062:
                    conn.rollback()
                    return False
                conn.rollback()
                raise


# ─── 외부 호환 헬퍼 ────────────────────────────────────────────────
# 기존 pipeline.py가 save_to_api(batch, url)로 호출했다면, 점진적 전환을 위해
# 같은 시그니처로 살려둘 수 있음. 새 코드는 DataManager를 직접 쓰는 게 권장.

_default_manager: Optional[DataManager] = None


def _get_default_manager() -> DataManager:
    global _default_manager
    if _default_manager is None:
        _default_manager = DataManager()
    return _default_manager


def save_to_db(batch: List[Dict]) -> Dict[str, int]:
    """기존 save_to_api 자리에 그대로 대체할 수 있는 함수 형태."""
    return _get_default_manager().save_posts(batch)


def isolate_and_save(batch: List[Dict]) -> Dict[str, int]:
    """
    이전 isolate_and_save와 동일한 시그니처 유지.
    하지만 save_posts가 이미 건별 트랜잭션이라 분할 재귀 불필요.
    같은 결과 반환.
    """
    return _get_default_manager().save_posts(batch)
