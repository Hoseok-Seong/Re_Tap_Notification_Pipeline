# Measurement

This directory contains local measurement scripts for the notification pipeline.

## Browser UI

Run a local UI when you want to start long measurements manually and inspect CSV rows.

```bash
python3 measurement/ui/server.py
```

Open `http://127.0.0.1:8090`, choose `Kafka Pipeline` or `Sequential Baseline`, set the count and label, then click `실행`.

Use the `FCM Mock 설정` panel to change the mock response delay or failure rate before a run. Applying this setting recreates only the FCM Mock container, so the mock metrics counters reset to zero.

The UI runs the existing scripts in the background and appends results to:

- `measurement/results/pipeline_experiment.csv`
- `measurement/results/baseline.csv`

Per-run logs are written under `measurement/results/ui_logs/`.

## Baseline

Sequentially call FCM Mock without Kafka:

```bash
python3 measurement/scripts/run_baseline.py --count 1000
```

Default output:

```text
measurement/results/baseline.csv
```

## Kafka Pipeline

Measure Producer -> Kafka -> Consumer -> FCM Mock:

```bash
python3 measurement/scripts/run_pipeline_experiment.py --count 1000
```

The script updates `letter.id <= count` to `CURDATE()` before triggering the Producer, so the existing seed data remains usable across days.

Default output:

```text
measurement/results/pipeline_experiment.csv
```

## Notes

- FCM Mock metrics are cumulative, so scripts record before/after deltas.
- Run with a low `--count` first to verify the setup.
- For repeatable failure-free throughput tests, set the FCM Mock failure rate to `0`.
- The FCM batch mock models the Firebase Admin SDK multicast/batch interface with up to 500 messages per request. The configured delay is applied once per batch request, so this measures the impact of reducing HTTP round trips rather than reproducing real FCM latency.

## First Results

Environment:

- Local Docker Desktop
- Kafka partitions: 10
- Consumer max poll records: 500
- FCM Mock delay: 50 ms
- FCM Mock failure rate: 0%
- Message count: 10,000

| Scenario | Elapsed seconds | Throughput msg/s | Notes |
|---|---:|---:|---|
| Sequential baseline | 591.633 | 16.902 | Direct FCM Mock calls without Kafka |
| Kafka pipeline before FCM batch | 589.000 | 16.978 | Producer published 10,000 messages in 178 ms; Consumer called FCM Mock one message at a time |
| Kafka pipeline with FCM batch | 1.701 | 5,879.209 | User-run UI measurement; 500-message batch calls; failure rate 0% |
| Kafka pipeline with FCM batch, clean rerun | 1.672 | 5,981.840 | DLT topic drained before measurement; failure rate 0% |
| Kafka pipeline with FCM batch, 2% failures | 1.698 | 5,888.581 | Failure handling smoke result; 202 failures out of 10,000 matched the configured 2% failure rate |

The first pipeline result was close to the sequential baseline because the Consumer polled Kafka in batches but called FCM Mock one message at a time. After changing the Consumer to call the FCM batch mock once per Kafka batch, 10,000 messages completed in about 1.7 seconds at roughly 5.9k msg/s.

The batch result should be interpreted as a local simulation of Admin SDK-style batching. It demonstrates the structural gain from reducing HTTP calls from 10,000 requests to roughly 20 batch requests at 500 messages per batch; it does not claim that production FCM always processes 500 messages in the same latency as one message.
