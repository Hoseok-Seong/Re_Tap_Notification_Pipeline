#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
import os
import subprocess
import sys
import threading
from dataclasses import asdict, dataclass
from datetime import datetime
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse
from urllib.request import urlopen


PROJECT_ROOT = Path(__file__).resolve().parents[2]
UI_DIR = PROJECT_ROOT / "measurement" / "ui"
RESULTS_DIR = PROJECT_ROOT / "measurement" / "results"
LOG_DIR = RESULTS_DIR / "ui_logs"


@dataclass
class JobState:
    running: bool = False
    kind: str | None = None
    label: str | None = None
    command: list[str] | None = None
    started_at: str | None = None
    completed_at: str | None = None
    return_code: int | None = None
    log_file: str | None = None


class MeasurementState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.job = JobState()

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            return asdict(self.job)

    def start(self, kind: str, label: str, command: list[str], log_file: Path) -> None:
        with self.lock:
            if self.job.running:
                raise RuntimeError("A measurement job is already running")
            self.job = JobState(
                running=True,
                kind=kind,
                label=label,
                command=command,
                started_at=now_iso(),
                log_file=str(log_file.relative_to(PROJECT_ROOT)),
            )

    def complete(self, return_code: int) -> None:
        with self.lock:
            self.job.running = False
            self.job.completed_at = now_iso()
            self.job.return_code = return_code


STATE = MeasurementState()


def now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


def run_job(kind: str, label: str, command: list[str], log_file: Path) -> None:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    with log_file.open("w") as log:
        log.write(f"started_at={now_iso()}\n")
        log.write("command=" + " ".join(command) + "\n\n")
        log.flush()

        process = subprocess.Popen(
            command,
            cwd=PROJECT_ROOT,
            stdout=log,
            stderr=subprocess.STDOUT,
            text=True,
        )
        return_code = process.wait()
        log.write(f"\ncompleted_at={now_iso()}\n")
        log.write(f"return_code={return_code}\n")

    STATE.complete(return_code)


class MeasurementHandler(BaseHTTPRequestHandler):
    server_version = "RetapMeasurementUI/1.0"

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/":
            self.send_file(UI_DIR / "index.html", "text/html; charset=utf-8")
            return
        if parsed.path == "/app.js":
            self.send_file(UI_DIR / "app.js", "application/javascript; charset=utf-8")
            return
        if parsed.path == "/styles.css":
            self.send_file(UI_DIR / "styles.css", "text/css; charset=utf-8")
            return
        if parsed.path == "/api/status":
            self.send_json(STATE.snapshot())
            return
        if parsed.path == "/api/results":
            self.send_json(read_results())
            return
        if parsed.path == "/api/log":
            self.send_json({"text": read_latest_log()})
            return
        if parsed.path == "/api/fcm-config":
            self.send_json({"metrics": fcm_metrics()})
            return
        if parsed.path == "/api/consumer-config":
            self.send_json({"config": consumer_config()})
            return

        self.send_error(HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/fcm-config":
            self.apply_fcm_config()
            return

        if parsed.path == "/api/consumer-config":
            self.apply_consumer_config()
            return

        if parsed.path != "/api/run":
            self.send_error(HTTPStatus.NOT_FOUND)
            return

        try:
            payload = self.read_json()
            kind = str(payload.get("kind", "pipeline"))
            count = positive_int(payload.get("count"), "count")
            label = clean_label(str(payload.get("label") or default_label(kind, count)))
            if bool(payload.get("applyFcmBeforeRun", True)):
                delay_ms = non_negative_int(payload.get("delayMs"), "delayMs")
                failure_rate_percent = percent_value(payload.get("failureRatePercent"), "failureRatePercent")
                apply_fcm_config_values(
                    delay_ms,
                    failure_rate_percent,
                )
                wait_for_fcm_metrics(delay_ms, failure_rate_percent)
            if bool(payload.get("applyConsumerBeforeRun", True)):
                max_poll_records = positive_int(payload.get("maxPollRecords"), "maxPollRecords")
                apply_consumer_config_values(max_poll_records)
                wait_for_consumer_status()
            command = build_command(kind, count, label, payload)
            log_file = LOG_DIR / f"{datetime.now().strftime('%Y%m%d-%H%M%S')}-{label}.log"
            STATE.start(kind, label, command, log_file)
            thread = threading.Thread(
                target=run_job,
                args=(kind, label, command, log_file),
                daemon=True,
            )
            thread.start()
            self.send_json({"started": True, "label": label, "command": command}, HTTPStatus.ACCEPTED)
        except RuntimeError as e:
            self.send_json({"error": str(e)}, HTTPStatus.CONFLICT)
        except ValueError as e:
            self.send_json({"error": str(e)}, HTTPStatus.BAD_REQUEST)

    def apply_consumer_config(self) -> None:
        try:
            if STATE.snapshot()["running"]:
                raise RuntimeError("Cannot change Consumer config while a measurement job is running")

            payload = self.read_json()
            max_poll_records = positive_int(payload.get("maxPollRecords"), "maxPollRecords")
            apply_consumer_config_values(max_poll_records)
            self.send_json({"applied": True, "config": wait_for_consumer_config(max_poll_records)})
        except subprocess.CalledProcessError as e:
            self.send_json({"error": f"docker compose failed with exit code {e.returncode}"}, HTTPStatus.INTERNAL_SERVER_ERROR)
        except RuntimeError as e:
            self.send_json({"error": str(e)}, HTTPStatus.CONFLICT)
        except ValueError as e:
            self.send_json({"error": str(e)}, HTTPStatus.BAD_REQUEST)

    def apply_fcm_config(self) -> None:
        try:
            if STATE.snapshot()["running"]:
                raise RuntimeError("Cannot change FCM config while a measurement job is running")

            payload = self.read_json()
            delay_ms = non_negative_int(payload.get("delayMs"), "delayMs")
            failure_rate_percent = percent_value(payload.get("failureRatePercent"), "failureRatePercent")
            apply_fcm_config_values(delay_ms, failure_rate_percent)
            self.send_json({"applied": True, "metrics": wait_for_fcm_metrics(delay_ms, failure_rate_percent)})
        except subprocess.CalledProcessError as e:
            self.send_json({"error": f"docker compose failed with exit code {e.returncode}"}, HTTPStatus.INTERNAL_SERVER_ERROR)
        except RuntimeError as e:
            self.send_json({"error": str(e)}, HTTPStatus.CONFLICT)
        except ValueError as e:
            self.send_json({"error": str(e)}, HTTPStatus.BAD_REQUEST)

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length).decode("utf-8")
        return json.loads(raw) if raw else {}

    def send_json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_file(self, path: Path, content_type: str) -> None:
        body = path.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: Any) -> None:
        sys.stderr.write("[%s] %s\n" % (now_iso(), format % args))


def positive_int(value: Any, name: str) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError) as e:
        raise ValueError(f"{name} must be an integer") from e
    if number <= 0:
        raise ValueError(f"{name} must be positive")
    return number


def non_negative_int(value: Any, name: str) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError) as e:
        raise ValueError(f"{name} must be an integer") from e
    if number < 0:
        raise ValueError(f"{name} must be non-negative")
    return number


def percent_value(value: Any, name: str) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError) as e:
        raise ValueError(f"{name} must be numeric") from e
    if number < 0 or number > 100:
        raise ValueError(f"{name} must be between 0 and 100")
    return number


def apply_fcm_config_values(delay_ms: int, failure_rate_percent: float) -> None:
    command = [
        "docker",
        "compose",
        "up",
        "-d",
        "--force-recreate",
        "fcm-mock-server",
    ]
    env = {
        **os.environ,
        "FCM_MOCK_RESPONSE_DELAY_MS": str(delay_ms),
        "FCM_MOCK_FAILURE_RATE_PERCENT": str(failure_rate_percent),
    }
    subprocess.run(command, cwd=PROJECT_ROOT, env=env, check=True)


def apply_consumer_config_values(max_poll_records: int) -> None:
    command = [
        "docker",
        "compose",
        "up",
        "-d",
        "--force-recreate",
        "notification-consumer",
    ]
    env = {
        **os.environ,
        "KAFKA_MAX_POLL_RECORDS": str(max_poll_records),
    }
    subprocess.run(command, cwd=PROJECT_ROOT, env=env, check=True)


def clean_label(value: str) -> str:
    allowed = []
    for character in value.strip():
        if character.isalnum() or character in ("-", "_", "."):
            allowed.append(character)
    label = "".join(allowed)
    if not label:
        raise ValueError("label must contain at least one safe character")
    return label[:80]


def default_label(kind: str, count: int) -> str:
    return f"{kind}-{count}-{datetime.now().strftime('%Y%m%d-%H%M%S')}"


def build_command(kind: str, count: int, label: str, payload: dict[str, Any]) -> list[str]:
    timeout_seconds = positive_int(payload.get("timeoutSeconds", 900), "timeoutSeconds")

    if kind == "baseline":
        return [
            sys.executable,
            "measurement/scripts/run_baseline.py",
            "--count",
            str(count),
            "--label",
            label,
        ]

    if kind == "pipeline":
        command = [
            sys.executable,
            "measurement/scripts/run_pipeline_experiment.py",
            "--count",
            str(count),
            "--label",
            label,
            "--timeout-seconds",
            str(timeout_seconds),
        ]
        if bool(payload.get("skipPrepareArrivals")):
            command.append("--skip-prepare-arrivals")
        return command

    raise ValueError("kind must be baseline or pipeline")


def read_results() -> dict[str, Any]:
    return {
        "baseline": read_csv_tail(RESULTS_DIR / "baseline.csv"),
        "pipeline": read_csv_tail(RESULTS_DIR / "pipeline_experiment.csv"),
    }


def read_csv_tail(path: Path, limit: int = 20) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open(newline="") as file:
        rows = list(csv.DictReader(file))
    return rows[-limit:]


def read_latest_log() -> str:
    if not LOG_DIR.exists():
        return ""
    logs = sorted(LOG_DIR.glob("*.log"), key=lambda path: path.stat().st_mtime, reverse=True)
    if not logs:
        return ""
    return logs[0].read_text(errors="replace")[-12000:]


def fcm_metrics() -> dict[str, Any]:
    with urlopen("http://localhost:8080/metrics", timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def consumer_status() -> dict[str, Any]:
    with urlopen("http://localhost:8082/status", timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def consumer_config() -> dict[str, Any]:
    max_poll_records = docker_exec_stdout([
        "docker",
        "compose",
        "exec",
        "-T",
        "notification-consumer",
        "printenv",
        "KAFKA_MAX_POLL_RECORDS",
    ])
    status: dict[str, Any] = {}
    try:
        status = consumer_status()
    except Exception:
        status = {"available": False}
    return {
        "maxPollRecords": int(max_poll_records) if max_poll_records.isdigit() else None,
        "status": status,
    }


def docker_exec_stdout(command: list[str]) -> str:
    try:
        completed = subprocess.run(
            command,
            cwd=PROJECT_ROOT,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
        )
        return completed.stdout.strip()
    except subprocess.CalledProcessError:
        return ""


def wait_for_fcm_metrics(expected_delay_ms: int | None = None, expected_failure_rate_percent: float | None = None) -> dict[str, Any]:
    import time

    deadline = time.monotonic() + 30
    latest_error: Exception | None = None
    consecutive_successes = 0
    latest: dict[str, Any] = {}
    while time.monotonic() < deadline:
        try:
            latest = fcm_metrics()
            if expected_delay_ms is not None and int(latest["responseDelayMs"]) != expected_delay_ms:
                consecutive_successes = 0
                time.sleep(0.5)
                continue
            if (
                    expected_failure_rate_percent is not None
                    and float(latest["failureRatePercent"]) != expected_failure_rate_percent
            ):
                consecutive_successes = 0
                time.sleep(0.5)
                continue

            consecutive_successes += 1
            if consecutive_successes >= 3:
                return latest
            time.sleep(0.5)
        except Exception as e:
            latest_error = e
            consecutive_successes = 0
            time.sleep(0.5)
    raise RuntimeError(f"FCM Mock did not become ready: latest={latest}, error={latest_error}")


def wait_for_consumer_status() -> dict[str, Any]:
    import time

    deadline = time.monotonic() + 45
    latest_error: Exception | None = None
    consecutive_successes = 0
    latest: dict[str, Any] = {}
    while time.monotonic() < deadline:
        try:
            latest = consumer_status()
            consecutive_successes += 1
            if consecutive_successes >= 3:
                return latest
            time.sleep(0.5)
        except Exception as e:
            latest_error = e
            consecutive_successes = 0
            time.sleep(0.5)
    raise RuntimeError(f"Consumer did not become ready: latest={latest}, error={latest_error}")


def wait_for_consumer_config(expected_max_poll_records: int) -> dict[str, Any]:
    import time

    deadline = time.monotonic() + 45
    latest: dict[str, Any] = {}
    while time.monotonic() < deadline:
        wait_for_consumer_status()
        latest = consumer_config()
        if latest.get("maxPollRecords") == expected_max_poll_records:
            return latest
        time.sleep(0.5)
    raise RuntimeError(f"Consumer config did not become ready: latest={latest}")


def main() -> None:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
    server = ThreadingHTTPServer(("127.0.0.1", port), MeasurementHandler)
    print(f"Measurement UI: http://127.0.0.1:{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
