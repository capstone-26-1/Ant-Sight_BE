import argparse
from crawler import crawl_stock, discover_last_page
from cleaner import clean_records
from storage import save_to_api
from checkpoint import is_completed, mark_stock_completed, mark_stock_failed
from stock_fetcher import get_all_stock_codes


def run_one_stock(stock_code, api_url, start_page=1, end_page=None, skip_if_complete=False):
    if skip_if_complete and is_completed(stock_code):
        print(f"☑️ 이미 완료된 종목: {stock_code}")
        return

    try:
        if end_page is None:
            end_page = discover_last_page(stock_code)

        print(f"▶ 시작: {stock_code} ({start_page} ~ {end_page})")

        raw_records = crawl_stock(stock_code, start_page=start_page, end_page=end_page)
        cleaned = clean_records(raw_records)

        if cleaned:
            save_to_api(cleaned, api_url)
            print(f"✅ 저장 완료: {stock_code}, {len(cleaned)}건")
        else:
            print(f"⚠ 저장할 데이터 없음: {stock_code}")

        mark_stock_completed(stock_code, end_page)

    except Exception as e:
        mark_stock_failed(stock_code, str(e))
        print(f"❌ 실패: {stock_code} / {e}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--code", required=False)
    parser.add_argument("--api-url", default="http://localhost:7689")
    parser.add_argument("--start-page", type=int, default=1)
    parser.add_argument("--end-page", type=int, default=None)
    parser.add_argument("--skip-if-complete", action="store_true")

    args = parser.parse_args()

    if args.code:
        stock_codes = [args.code]
    else:
        stock_codes = get_all_stock_codes()

    print(f"총 대상 종목 수: {len(stock_codes)}")

    for stock_code in stock_codes:
        run_one_stock(
            stock_code=stock_code,
            api_url=args.api_url,
            start_page=args.start_page,
            end_page=args.end_page,
            skip_if_complete=args.skip_if_complete
        )


if __name__ == "__main__":
    main()