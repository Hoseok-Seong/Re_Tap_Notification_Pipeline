# Measurement

This directory contains local measurement scripts for the notification pipeline.

## Browser UI

Run a local UI when you want to start long measurements manually and inspect CSV rows.

```bash
python3 measurement/ui/server.py
```

Open `http://127.0.0.1:8090`, choose `Kafka Pipeline` or `Sequential Baseline`, set the count and label, then click `실행`.

Use the `FCM Mock 설정` panel to change the mock response delay or failure rate before a run. Applying this setting recreates only the FCM Mock container, so the mock metrics counters reset to zero.

Use the `Consumer 설정` panel to change `max.poll.records` before a run. Applying this setting recreates only the Consumer container.
The UI preserves and re-checks the current FCM Mock settings after recreating the Consumer, so changing `max.poll.records` should not reset FCM delay or failure rate.

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
- The Docker Compose default FCM Mock failure rate is `0`; increase it only for DLT/failure handling checks.
- The FCM batch mock models the Firebase Admin SDK multicast/batch interface with up to 500 messages per request. The configured delay is applied once per batch request, so this measures the impact of reducing HTTP round trips rather than reproducing real FCM latency.
- Use 10,000-message runs for parameter sweeps, then run the final 1,000,000-message measurement with the best settings. This keeps iteration fast while preserving the project goal of validating million-scale behavior.

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

## FCM Delay Experiment

Environment:

- Local Docker Desktop
- Kafka partitions: 10
- Consumer max poll records: 500
- FCM Mock failure rate: 0%
- Message count: 10,000

| FCM Mock delay | Elapsed seconds | Throughput msg/s |
|---:|---:|---:|
| 20 ms | 1.152 | 8,682.134 |
| 50 ms | 1.641 | 6,095.309 |
| 100 ms | 2.655 | 3,767.124 |

Throughput decreases as the configured per-batch FCM delay increases, which matches the expected bottleneck shift: Kafka publishing remains fast, while Consumer completion time is bounded by the number of FCM batch calls multiplied by mock latency.

## Consumer Batch Size Experiment

Environment:

- Local Docker Desktop
- Kafka partitions: 10
- FCM Mock delay: 50 ms
- FCM Mock failure rate: 0%
- Message count: 10,000

| Consumer max.poll.records | Elapsed seconds | Throughput msg/s |
|---:|---:|---:|
| 100 | 6.226 | 1,606.088 |
| 200 | 3.675 | 2,721.415 |
| 500 | 1.653 | 6,048.868 |

Larger Consumer poll batches reduce the number of FCM batch calls. With the current FCM Mock model, `max.poll.records=500` is the strongest local setting because it matches the mock FCM batch limit and minimizes per-batch delay overhead.
