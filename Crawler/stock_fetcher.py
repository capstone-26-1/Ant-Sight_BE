import csv
from pathlib import Path


def get_all_stock_codes(csv_path: str = "stock_codes.csv") -> list[str]:
    path = Path(csv_path)

    if not path.exists():
        raise FileNotFoundError(f"종목코드 파일을 찾을 수 없습니다: {path.resolve()}")

    stock_codes: list[str] = []

    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)

        # 가능한 컬럼명 후보
        candidates = ["code", "stock_code", "종목코드", "종목코드 ", "Code"]

        fieldnames = reader.fieldnames or []
        code_column = None

        for candidate in candidates:
            if candidate in fieldnames:
                code_column = candidate
                break

        if code_column is None:
            raise ValueError(
                f"종목코드 컬럼을 찾지 못했습니다. 현재 컬럼: {fieldnames}"
            )

        for row in reader:
            raw = str(row.get(code_column, "")).strip()
            if not raw:
                continue

            if raw.upper().startswith("A"):
                raw = raw[1:]

            # 6자리 코드 형태로 맞춤
            code = raw.zfill(6)
            stock_codes.append(code)

    return stock_codes