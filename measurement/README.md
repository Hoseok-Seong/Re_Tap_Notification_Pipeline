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
