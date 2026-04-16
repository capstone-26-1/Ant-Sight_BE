import time
import json
import re
import requests
from bs4 import BeautifulSoup
from requests.exceptions import ReadTimeout, RequestException

DEFAULT_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Referer": "https://finance.naver.com",
    "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Connection": "keep-alive",
}


def is_blocked_response(text: str) -> bool:
    if not text:
        return True
    blocked_signals = ["history.back()", "alert(", "잘못된 접근", "비정상적인 접근"]
    return any(sig in text for sig in blocked_signals)


def normalize_text(text: str) -> str:
    if not text:
        return ""

    # URL 제거
    text = re.sub(r"https?://\S+|www\.\S+", " ", text)

    # 공백 정리
    text = text.replace("\u200b", " ")   # zero-width space
    text = text.replace("\xa0", " ")     # nbsp
    text = re.sub(r"\s+", " ", text).strip()

    return text


def get_with_retry(session, url, retries=3, connect_timeout=5, read_timeout=8):
    last_error = None

    for attempt in range(1, retries + 1):
        req_start = time.perf_counter()
        try:
            res = session.get(url, timeout=(connect_timeout, read_timeout))
            res.raise_for_status()
            req_elapsed = time.perf_counter() - req_start
            print(f"   ✅ 요청 성공 ({attempt}/{retries}) - {req_elapsed:.2f}초")
            return res
        except ReadTimeout as e:
            last_error = e
            req_elapsed = time.perf_counter() - req_start
            print(f"   ⏱️ timeout ({attempt}/{retries}) - {req_elapsed:.2f}초 - {url}")
        except RequestException as e:
            last_error = e
            req_elapsed = time.perf_counter() - req_start
            print(f"   ⚠️ request 실패 ({attempt}/{retries}) - {req_elapsed:.2f}초 - {repr(e)}")

        if attempt < retries:
            time.sleep(0.5)

    raise last_error


def create_session() -> requests.Session:
    session = requests.Session()
    session.headers.update(DEFAULT_HEADERS)
    return session


def discover_last_page(stock_code: str, max_probe: int = 5000) -> int:
    session = create_session()
    base_url = f"https://finance.naver.com/item/board.naver?code={stock_code}&page={{}}"

    prev_list_url = base_url.format(1)
    last_valid = 1

    for page in range(1, max_probe + 1):
        url = base_url.format(page)
        session.headers["Referer"] = prev_list_url

        res = get_with_retry(session, url)
        res.encoding = res.apparent_encoding

        if is_blocked_response(res.text):
            raise RuntimeError(f"차단 응답 감지: page={page}")

        soup = BeautifulSoup(res.text, "html.parser")
        title_links = soup.select("table.type2 tr td.title a")

        if len(title_links) == 0:
            return page - 1

        last_valid = page
        prev_list_url = url

    return last_valid


def crawl_stock(stock_code: str, start_page: int = 1, end_page: int | None = None) -> list[dict]:
    session = create_session()
    base_url = f"https://finance.naver.com/item/board.naver?code={stock_code}&page={{}}"

    if end_page is None:
        end_page = discover_last_page(stock_code)

    results = []
    prev_list_url = base_url.format(max(1, start_page - 1))

    for page in range(start_page, end_page + 1):
        page_start = time.perf_counter()
        print(f"\n📄 [{stock_code}] page {page} 크롤링 중...")

        url = base_url.format(page)
        session.headers["Referer"] = prev_list_url

        try:
            res = get_with_retry(session, url)
        except Exception as e:
            print(f"❌ 목록 페이지 실패(page {page}): {repr(e)}")
            continue

        res.encoding = res.apparent_encoding

        if is_blocked_response(res.text):
            print(f"🚫 차단 응답 감지(page {page})")
            break

        soup = BeautifulSoup(res.text, "html.parser")
        rows = soup.select("table.type2 tr")
        title_links = soup.select("table.type2 tr td.title a")
        print(f"   rows={len(rows)}, title_links={len(title_links)}")

        if len(title_links) == 0:
            print(f"✅ 마지막 페이지 도달: {page - 1}")
            break

        page_item_count = 0

        for row in rows:
            cols = row.select("td")
            if len(cols) < 6:
                continue

            title_tag = cols[1].select_one("a")
            if not title_tag:
                continue

            # 제목 안 댓글수 추출: [1], [12] 등
            comment_tag = (
                title_tag.select_one("em")
                or title_tag.select_one("strong")
                or title_tag.select_one("span")
            )

            comments = "0"
            if comment_tag:
                raw = comment_tag.get_text(strip=True)
                m = re.search(r"\[(\d+)\]", raw)
                if m:
                    comments = m.group(1)
                comment_tag.decompose()

            date = cols[0].get_text(strip=True)
            title = normalize_text(title_tag.get_text(strip=True))
            link = "https://finance.naver.com" + title_tag["href"]
            writer = normalize_text(cols[2].get_text(strip=True))
            views = cols[3].get_text(strip=True)
            likes = cols[4].get_text(strip=True)
            dislikes = cols[5].get_text(strip=True)
            text = ""

            try:
                nid = link.split("nid=")[1].split("&")[0]
                detail_url = f"https://m.stock.naver.com/pc/domestic/stock/{stock_code}/discussion/{nid}"

                session.headers["Referer"] = link
                detail_res = get_with_retry(session, detail_url, retries=2, connect_timeout=5, read_timeout=8)
                detail_res.encoding = detail_res.apparent_encoding
                detail_soup = BeautifulSoup(detail_res.text, "html.parser")

                script_tag = detail_soup.select_one("#__NEXT_DATA__")
                if script_tag and script_tag.string:
                    data_json = json.loads(script_tag.string)
                    post = data_json["props"]["pageProps"]["dehydratedState"]["queries"][1]["state"]["data"]["result"]

                    content_json_str = post.get("contentJsonSwReplaced")

                    if isinstance(content_json_str, str):
                        try:
                            content_json = json.loads(content_json_str)
                            texts = []
                            for comp in content_json.get("document", {}).get("components", []):
                                for v in comp.get("value", []):
                                    for node in v.get("nodes", []):
                                        value = node.get("value", "")
                                        if value:
                                            texts.append(value)
                            text = " ".join(texts).strip()
                        except Exception:
                            pass

                    if not text:
                        html_content = post.get("contentHtml")
                        if isinstance(html_content, str) and html_content.strip():
                            soup_tmp = BeautifulSoup(html_content, "html.parser")
                            text = soup_tmp.get_text(separator=" ", strip=True)

                    if not text:
                        text = post.get("title", "")

            except Exception as e:
                print(f"   ❌ 본문 크롤링 실패: {repr(e)}")
                text = title

            text = normalize_text(text)

            results.append({
                "stockCode": stock_code,
                "writer": writer,
                "title": title,
                "text": text,
                "timestamp": date,
                "likes": likes,
                "dislikes": dislikes,
                "views": views,
                "comments": comments,
                "page": page,
            })

            page_item_count += 1
            session.headers["Referer"] = url

        page_elapsed = time.perf_counter() - page_start
        print(f"✅ [{stock_code}] page {page} 완료 - 게시글 {page_item_count}개 - 총 {page_elapsed:.2f}초")

        prev_list_url = url

    return results