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


def _parse_bulk_response(resp: requests.Response, fallback_len: int) -> Dict[str, int]:
    """
    Spring BulkResponse({total, inserted, skipped}) 를 파싱.
    파싱 실패 시 모두 inserted 로 간주(보수적 fallback).
    """
    try:
        body = resp.json()
        return {
            "inserted": int(body.get("inserted", 0)),
            "skipped":  int(body.get("skipped", 0)),
        }
    except Exception:
        return {"inserted": fallback_len, "skipped": 0}


def try_post_with_retry(
    batch: List[Dict],
    url: str,
    retries: int = 2,
    delay: float = 1.0,
) -> requests.Response:
    last_resp = None

    for attempt in range(1, retries + 2):
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
) -> Dict[str, int]:
    """
    실패한 배치를 반으로 나눠 재귀 저장.
    {"inserted": N, "skipped": N, "failed": N} 반환.
    """
    empty = {"inserted": 0, "skipped": 0, "failed": 0}

    if not batch:
        return empty

    print(f"🔎 분할 저장 시도 ({len(batch)}건)")
    print_sample("[SPLIT SAMPLE]", batch[0])

    resp = try_post_with_retry(batch, url, retries=1, delay=0.8)

    if resp is not None and resp.status_code < 400:
        parsed = _parse_bulk_response(resp, fallback_len=len(batch))
        print(f"✅ 분할 배치 저장 성공 (inserted={parsed['inserted']}, skipped={parsed['skipped']})")
        return {**parsed, "failed": 0}

    if len(batch) <= min_split_size:
        print("❌ 최종 실패 레코드:")
        print_sample("[BAD RECORD]", batch[0])
        return {"inserted": 0, "skipped": 0, "failed": len(batch)}

    mid = len(batch) // 2
    left  = isolate_and_save(batch[:mid], url, min_split_size=min_split_size)
    right = isolate_and_save(batch[mid:], url, min_split_size=min_split_size)

    return {k: left[k] + right[k] for k in ("inserted", "skipped", "failed")}


def save_to_api(
    records: List[Dict],
    api_base_url: str,
    batch_size: int = 50,
    retries: int = 2,
    retry_delay: float = 1.0,
) -> Dict[str, int]:
    """
    {"total": N, "inserted": N, "skipped": N, "failed": N} 반환.
    - inserted: 실제로 DB에 새로 들어간 건수
    - skipped : (stock_code + title + timestamp) 중복으로 스킵된 건수
    - failed  : 서버 오류/유효성 실패로 끝내 저장 못 한 건수
    """
    stats = {"total": len(records), "inserted": 0, "skipped": 0, "failed": 0}

    if not records:
        print("⚠️ 저장할 데이터 없음")
        return stats

    url = f"{api_base_url.rstrip('/')}/posts/bulk"

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
            parsed = _parse_bulk_response(resp, fallback_len=len(batch))
            stats["inserted"] += parsed["inserted"]
            stats["skipped"]  += parsed["skipped"]
            print(
                f"✅ batch {idx} 저장 완료 "
                f"(inserted={parsed['inserted']}, skipped={parsed['skipped']}) / "
                f"누적 inserted={stats['inserted']}, skipped={stats['skipped']}"
            )
            continue

        print(f"🚨 batch {idx} 실패 - 분할 저장 시작")
        split = isolate_and_save(batch, url, min_split_size=1)
        stats["inserted"] += split["inserted"]
        stats["skipped"]  += split["skipped"]
        stats["failed"]   += split["failed"]

        if split["failed"] > 0:
            print(f"⚠️ 일부 레코드 실패: {split['failed']}건 (skip)")

    print(
        f"✅ API 저장 완료: 총 {stats['total']}건 / "
        f"inserted {stats['inserted']}건 / "
        f"skipped(중복) {stats['skipped']}건 / "
        f"failed {stats['failed']}건"
    )
    return stats
