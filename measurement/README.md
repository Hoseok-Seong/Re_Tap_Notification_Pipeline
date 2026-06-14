# Measurement

This directory contains local measurement scripts for the notification pipeline.

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
| Kafka pipeline | 589.000 | 16.978 | Producer published 10,000 messages in 178 ms; Consumer FCM calls are currently sequential |

The first pipeline result is close to the sequential baseline because the current Consumer polls Kafka in batches but calls FCM Mock one message at a time. The next optimization target is parallelizing or true-batching the FCM send path before running partition/batch-size experiments.
