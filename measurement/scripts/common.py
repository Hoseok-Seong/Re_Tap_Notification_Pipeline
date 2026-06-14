from __future__ import annotations

import csv
import json
import os
import subprocess
import time
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen


PROJECT_ROOT = Path(__file__).resolve().parents[2]
RESULTS_DIR = PROJECT_ROOT / "measurement" / "results"


def load_dotenv(path: Path = PROJECT_ROOT / ".env") -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values

    for raw_line in path.read_text().splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def env_or_dotenv(key: str, default: str | None = None) -> str:
    dotenv = load_dotenv()
    value = os.environ.get(key) or dotenv.get(key) or default
    if value is None:
        raise RuntimeError(f"{key} is required")
    return value


def now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


def http_json(method: str, url: str, body: dict[str, Any] | None = None, timeout: float = 30.0) -> dict[str, Any]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=timeout) as response:
            payload = response.read().decode("utf-8")
            return json.loads(payload) if payload else {}
    except HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {method} {url} failed. status={e.code} body={detail}") from e


def fcm_metrics(fcm_url: str) -> dict[str, Any]:
    return http_json("GET", f"{fcm_url.rstrip('/')}/metrics", timeout=10.0)


def wait_for_fcm_total_delta(
    fcm_url: str,
    initial_total: int,
    expected_delta: int,
    timeout_seconds: float,
    poll_interval_seconds: float = 0.5,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    latest = fcm_metrics(fcm_url)

    while time.monotonic() < deadline:
        latest = fcm_metrics(fcm_url)
        if int(latest["totalRequests"]) >= initial_total + expected_delta:
            return latest
        time.sleep(poll_interval_seconds)

    raise TimeoutError(
        "Timed out waiting for FCM Mock requests. "
        f"initial={initial_total}, expectedDelta={expected_delta}, latest={latest}"
    )


def prepare_arrivals_for_today(count: int) -> None:
    sql = f"UPDATE letter SET arrival_date = CURDATE() WHERE id <= {count}"

    subprocess.run(
        [
            "docker",
            "compose",
            "exec",
            "-T",
            "mariadb",
            "sh",
            "-c",
            'MYSQL_PWD="$MARIADB_PASSWORD" mariadb -u "$MARIADB_USER" "$MARIADB_DATABASE" -e "$1"',
            "sh",
            sql,
        ],
        cwd=PROJECT_ROOT,
        check=True,
    )


def append_csv(path: Path, row: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    write_header = not path.exists()
    with path.open("a", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(row.keys()))
        if write_header:
            writer.writeheader()
        writer.writerow(row)


def rate_per_second(count: int, elapsed_seconds: float) -> float:
    if count == 0 or elapsed_seconds <= 0:
        return 0.0
    return count / elapsed_seconds
