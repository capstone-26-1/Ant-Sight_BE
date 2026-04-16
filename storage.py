import time
import requests
from typing import List, Dict


def chunked(seq, size):
    for i in range(0, len(seq), size):
        yield seq[i:i + size]


def print_sample(prefix: str, record: Dict) -> None:
    print(prefix)
    print("title =", record.get("title"))
    print("writer =", record.get("writer"))
    print("timestamp =", record.get("timestamp"))
    print("comments =", record.get("comments"))
    print("text =", str(record.get("text", ""))[:150])


def post_batch(batch: List[Dict], url: str, timeout: int = 60) -> requests.Response:
    payload = {"posts": batch}
    return requests.post(url, json=payload, timeout=timeout)


def try_post_with_retry(
    batch: List[Dict],
    url: str,
    retries: int = 2,
    delay: float = 1.0,
) -> requests.Response:
    last_resp = None

    for attempt in range(1, retries + 2):  # 최초 1회 + 재시도 retries회
        try:
            resp = post_batch(batch, url)
            print(f"📥 status={resp.status_code}")
            print(f"📥 body={resp.text[:500]}")

            if resp.status_code < 500:
                return resp

            last_resp = resp
            print(f"⚠️ 서버 오류, 재시도 예정 ({attempt}/{retries + 1})")

        except requests.RequestException as e:
            print(f"⚠️ 요청 예외 ({attempt}/{retries + 1}): {repr(e)}")

        if attempt < retries + 1:
            time.sleep(delay)

    return last_resp


def isolate_and_save(
    batch: List[Dict],
    url: str,
    min_split_size: int = 1,
) -> int:
    """
    실패한 배치를 반으로 나눠 재귀 저장.
    성공적으로 저장된 건수를 반환.
    """

    if not batch:
        return 0

    print(f"🔎 분할 저장 시도 ({len(batch)}건)")
    print_sample("[SPLIT SAMPLE]", batch[0])

    resp = try_post_with_retry(batch, url, retries=1, delay=0.8)

    if resp is not None and resp.status_code < 400:
        inserted = len(batch)
        print(f"✅ 분할 배치 저장 성공 ({inserted}건)")
        return inserted

    if len(batch) <= min_split_size:
        print("❌ 최종 실패 레코드:")
        print_sample("[BAD RECORD]", batch[0])
        return 0

    mid = len(batch) // 2
    left = batch[:mid]
    right = batch[mid:]

    saved_left = isolate_and_save(left, url, min_split_size=min_split_size)
    saved_right = isolate_and_save(right, url, min_split_size=min_split_size)

    return saved_left + saved_right


def save_to_api(
    records: List[Dict],
    api_base_url: str,
    batch_size: int = 50,
    retries: int = 2,
    retry_delay: float = 1.0,
) -> None:
    if not records:
        print("⚠️ 저장할 데이터 없음")
        return

    url = f"{api_base_url.rstrip('/')}/posts/bulk"
    total = len(records)
    saved_total = 0

    for idx, batch in enumerate(chunked(records, batch_size), start=1):
        print(f"📡 POST {url} - batch {idx} ({len(batch)}건)")
        print_sample("[PAYLOAD DEBUG]", batch[0])

        resp = try_post_with_retry(
            batch=batch,
            url=url,
            retries=retries,
            delay=retry_delay,
        )

        if resp is not None and resp.status_code < 400:
            saved_total += len(batch)
            print(f"✅ batch {idx} 저장 완료 / 누적 {saved_total}/{total}")
            continue

        print(f"🚨 batch {idx} 실패 - 분할 저장 시작")
        saved = isolate_and_save(batch, url, min_split_size=1)
        saved_total += saved

        if saved < len(batch):
            failed_count = len(batch) - saved
            print(f"⚠️ 일부 레코드 실패: {failed_count}건 (skip)")

        print(f"✅ batch {idx} 분할 저장 완료 / 누적 {saved_total}/{total}")

    print(f"✅ API 저장 완료: 총 {saved_total}건")