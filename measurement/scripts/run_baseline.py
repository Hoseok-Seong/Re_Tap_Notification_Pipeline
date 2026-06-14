#!/usr/bin/env python3
from __future__ import annotations

import argparse
import time
import uuid
from pathlib import Path

from common import RESULTS_DIR, append_csv, fcm_metrics, http_json, now_iso, rate_per_second


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Measure sequential direct FCM Mock sends.")
    parser.add_argument("--count", type=int, default=1000, help="Number of direct FCM Mock sends.")
    parser.add_argument("--fcm-url", default="http://localhost:8080", help="FCM Mock base URL.")
    parser.add_argument("--output", type=Path, default=RESULTS_DIR / "baseline.csv")
    parser.add_argument("--label", default="sequential-baseline", help="Experiment label stored in CSV.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.count <= 0:
        raise ValueError("--count must be positive")

    before = fcm_metrics(args.fcm_url)
    success_count = 0
    failure_count = 0
    started = time.monotonic()

    for index in range(1, args.count + 1):
        body = {
            "message": {
                "token": f"mock-token-{index}",
                "notification": {
                    "title": f"baseline title {index}",
                    "body": "baseline body",
                },
                "data": {
                    "messageId": str(uuid.uuid4()),
                },
            }
        }
        try:
            http_json("POST", f"{args.fcm_url.rstrip('/')}/v1/projects/retap-local/messages:send", body, timeout=30.0)
            success_count += 1
        except Exception:
            failure_count += 1

    elapsed_seconds = time.monotonic() - started
    after = fcm_metrics(args.fcm_url)
    total_delta = int(after["totalRequests"]) - int(before["totalRequests"])

    row = {
        "timestamp": now_iso(),
        "label": args.label,
        "requested_count": args.count,
        "client_success_count": success_count,
        "client_failure_count": failure_count,
        "fcm_total_delta": total_delta,
        "fcm_success_delta": int(after["successRequests"]) - int(before["successRequests"]),
        "fcm_failure_delta": int(after["failureRequests"]) - int(before["failureRequests"]),
        "elapsed_seconds": round(elapsed_seconds, 3),
        "throughput_msg_per_second": round(rate_per_second(total_delta, elapsed_seconds), 3),
        "fcm_response_delay_ms": after.get("responseDelayMs"),
        "fcm_failure_rate_percent": after.get("failureRatePercent"),
    }
    append_csv(args.output, row)
    print(row)


if __name__ == "__main__":
    main()
