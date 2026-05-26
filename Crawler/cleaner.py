from typing import List, Dict


def to_int_or_zero(value: str) -> int:
    try:
        return int(str(value).replace(",", "").strip())
    except Exception:
        return 0


def clean_records(records: List[Dict]) -> List[Dict]:
    cleaned = []

    for r in records:
        title = str(r.get("title", "")).strip()
        writer = str(r.get("writer", "")).strip()
        text = str(r.get("text", "")).strip()

        # ❗ 핵심 필터
        if not title:
            continue
        if not writer:
            writer = "unknown"
        if not text:
            text = title  # fallback

        cleaned.append({
            "stock_code": r["stockCode"],
            "writer": writer,
            "title": title,
            "text": text,
            "timestamp": r["timestamp"],
            "likes": to_int_or_zero(r["likes"]),
            "dislikes": to_int_or_zero(r["dislikes"]),
            "views": to_int_or_zero(r["views"]),
            "comments": to_int_or_zero(r.get("comments", 0)),
        })

    return cleaned