#!/usr/bin/env python3
from __future__ import annotations

import argparse
import time
from pathlib import Path

from common import (
    RESULTS_DIR,
    append_csv,
    fcm_metrics,
    http_json,
    now_iso,
    prepare_arrivals_for_today,
    rate_per_second,
    wait_for_fcm_total_delta,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Measure Producer -> Kafka -> Consumer -> FCM Mock pipeline.")
    parser.add_argument("--count", type=int, default=1000, help="Number of notifications to publish.")
    parser.add_argument("--producer-url", default="http://localhost:8081", help="Notification Producer base URL.")
    parser.add_argument("--fcm-url", default="http://localhost:8080", help="FCM Mock base URL.")
    parser.add_argument("--timeout-seconds", type=float, default=300.0, help="Max wait time for FCM Mock metrics.")
    parser.add_argument("--output", type=Path, default=RESULTS_DIR / "pipeline_experiment.csv")
    parser.add_argument("--label", default="pipeline", help="Experiment label stored in CSV.")
    parser.add_argument(
        "--skip-prepare-arrivals",
        action="store_true",
        help="Do not update seed arrival_date values to CURDATE() before running.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.count <= 0:
        raise ValueError("--count must be positive")

    if not args.skip_prepare_arrivals:
        prepare_arrivals_for_today(args.count)

    before = fcm_metrics(args.fcm_url)
    started = time.monotonic()
    producer_response = http_json(
        "POST",
        f"{args.producer_url.rstrip('/')}/api/notifications/send?limit={args.count}",
        timeout=args.timeout_seconds,
    )
    published_count = int(producer_response["publishedCount"])
    after = wait_for_fcm_total_delta(
        args.fcm_url,
        int(before["totalRequests"]),
        published_count,
        args.timeout_seconds,
    )
    elapsed_seconds = time.monotonic() - started

    total_delta = int(after["totalRequests"]) - int(before["totalRequests"])
    success_delta = int(after["successRequests"]) - int(before["successRequests"])
    failure_delta = int(after["failureRequests"]) - int(before["failureRequests"])

    row = {
        "timestamp": now_iso(),
        "label": args.label,
        "requested_count": args.count,
        "published_count": published_count,
        "fcm_total_delta": total_delta,
        "fcm_success_delta": success_delta,
        "fcm_failure_delta": failure_delta,
        "producer_elapsed_millis": int(producer_response["elapsedMillis"]),
        "end_to_end_elapsed_seconds": round(elapsed_seconds, 3),
        "throughput_msg_per_second": round(rate_per_second(total_delta, elapsed_seconds), 3),
        "fcm_response_delay_ms": after.get("responseDelayMs"),
        "fcm_failure_rate_percent": after.get("failureRatePercent"),
    }
    append_csv(args.output, row)
    print(row)


if __name__ == "__main__":
    main()
