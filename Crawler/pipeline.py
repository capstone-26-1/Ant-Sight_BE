import argparse
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

# .env 자동 로드 (DB_HOST, DB_USER, DB_PASSWORD, DB_NAME)
from dotenv import load_dotenv
load_dotenv()

from crawler import crawl_stock, crawl_stock_incremental
from cleaner import clean_records
from storage import save_to_db
from checkpoint import is_completed, mark_stock_completed, mark_stock_failed
from stock_fetcher import get_all_stock_codes

KST = ZoneInfo("Asia/Seoul")
DEFAULT_END_PAGE = 1000


def run_full(
    stock_code: str,
    start_page: int = 1,
    end_page: int = DEFAULT_END_PAGE,
    skip_if_complete: bool = True,
) -> None:
    """전체 풀 크롤링 (초기 적재용)."""
    if skip_if_complete and is_completed(stock_code):
        print(f"☑️ 이미 완료된 종목: {stock_code}")
        return

    try:
        print(f"▶ [FULL] 시작: {stock_code} ({start_page} ~ {end_page})")

        raw_records, actual_last_page = crawl_stock(
            stock_code, start_page=start_page, end_page=end_page
        )
        cleaned = clean_records(raw_records)

        if cleaned:
            stats = save_to_db(cleaned)
            print(
                f"✅ 저장 완료: {stock_code} / "
                f"크롤링 {stats['total']}건 → "
                f"insert {stats['inserted']}건, "
                f"skip(중복) {stats['skipped']}건, "
                f"fail {stats['failed']}건"
            )
        else:
            print(f"⚠ 저장할 데이터 없음: {stock_code}")

        mark_stock_completed(stock_code, actual_last_page)

    except Exception as e:
        mark_stock_failed(stock_code, str(e))
        print(f"❌ 실패: {stock_code} / {e}")


def run_incremental(
    stock_code: str,
    cutoff: datetime,
    max_pages: int = 5,
) -> None:
    """증분 크롤링 (cutoff 이후 새 글만)."""
    try:
        raw_records = crawl_stock_incremental(
            stock_code, cutoff=cutoff, max_pages=max_pages
        )
        cleaned = clean_records(raw_records)

        if cleaned:
            stats = save_to_db(cleaned)
            print(
                f"✅ [INCR] {stock_code} / "
                f"신규 {stats['total']}건 → "
                f"insert {stats['inserted']}건, "
                f"skip {stats['skipped']}건, "
                f"fail {stats['failed']}건"
            )
        else:
            print(f"📭 [INCR] {stock_code} - 신규 게시글 없음")

    except Exception as e:
        # incremental은 한 종목 실패해도 다음 종목 진행
        print(f"❌ [INCR] {stock_code} 실패: {e}")


def main():
    parser = argparse.ArgumentParser(
        description="네이버 종토방 크롤링 → 정제 → RDS 직접 저장 파이프라인"
    )
    parser.add_argument(
        "--mode",
        choices=["full", "incremental"],
        default="full",
        help="full: 1~1000페이지 풀 크롤링 / incremental: 최근 N분 증분",
    )
    parser.add_argument(
        "--code",
        help="단일 종목코드 (예: 005930). 미지정 시 stock_codes.csv 전체 실행",
    )

    # full mode 전용 옵션
    parser.add_argument("--start-page", type=int, default=1)
    parser.add_argument("--end-page", type=int, default=DEFAULT_END_PAGE)
    parser.add_argument(
        "--skip-if-complete", action="store_true",
        help="checkpoint.json 기준 완료된 종목 건너뛰기 (full 모드 전용)",
    )

    # incremental mode 전용 옵션
    parser.add_argument(
        "--window-minutes", type=int, default=6,
        help="증분 모드 윈도우(분). 5분 cron + 1분 마진 권장 (기본 6).",
    )
    parser.add_argument(
        "--max-pages", type=int, default=5,
        help="증분 모드 최대 탐색 페이지 (기본 5)",
    )

    args = parser.parse_args()

    stock_codes = [args.code] if args.code else get_all_stock_codes()
    print(f"총 대상 종목 수: {len(stock_codes)} | mode={args.mode}")

    if args.mode == "incremental":
        # cutoff은 시작 시점에 한 번 고정 (모든 종목에 동일 적용)
        cutoff = datetime.now(KST) - timedelta(minutes=args.window_minutes)
        print(f"⏱️  cutoff={cutoff.strftime('%Y-%m-%d %H:%M')} (window={args.window_minutes}분)")

        for stock_code in stock_codes:
            run_incremental(
                stock_code=stock_code,
                cutoff=cutoff,
                max_pages=args.max_pages,
            )

        print(f"\n🏁 [INCR] 전체 완료: {len(stock_codes)}개 종목")

    else:  # full
        for stock_code in stock_codes:
            run_full(
                stock_code=stock_code,
                start_page=args.start_page,
                end_page=args.end_page,
                skip_if_complete=args.skip_if_complete,
            )


if __name__ == "__main__":
    main()
