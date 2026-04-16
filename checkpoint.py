import json
from pathlib import Path

CHECKPOINT_FILE = Path("checkpoint.json")


def load_checkpoint() -> dict:
    if not CHECKPOINT_FILE.exists():
        return {}
    return json.loads(CHECKPOINT_FILE.read_text(encoding="utf-8"))


def save_checkpoint(data: dict) -> None:
    CHECKPOINT_FILE.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )


def mark_stock_completed(stock_code: str, last_page: int) -> None:
    data = load_checkpoint()
    data[stock_code] = {
        "status": "completed",
        "lastPage": last_page,
    }
    save_checkpoint(data)


def mark_stock_failed(stock_code: str, message: str) -> None:
    data = load_checkpoint()
    data[stock_code] = {
        "status": "failed",
        "error": message,
    }
    save_checkpoint(data)


def is_completed(stock_code: str) -> bool:
    data = load_checkpoint()
    return data.get(stock_code, {}).get("status") == "completed"