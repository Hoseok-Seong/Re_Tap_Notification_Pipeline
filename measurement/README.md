# 측정 문서

이 디렉터리는 ReTap 알림 파이프라인의 로컬 측정 스크립트, 브라우저 UI, 결과 CSV를 포함합니다.

## 브라우저 측정 UI

긴 측정을 직접 실행하고 CSV 결과를 확인하려면 로컬 UI를 실행합니다.

```bash
python3 measurement/ui/server.py
```

브라우저에서 `http://127.0.0.1:8090`을 열고 다음 값을 설정한 뒤 `실행`을 누릅니다.

- 측정 종류: `Kafka Pipeline` 또는 `Sequential Baseline`
- 건수: 측정할 메시지 수
- 라벨: CSV에 기록될 실험 이름
- FCM Mock 설정: 지연 ms, 실패율 %
- Consumer 설정: `max.poll.records`

`FCM Mock 설정`을 적용하면 FCM Mock 컨테이너만 재생성되고, Mock 메트릭 카운터가 0으로 초기화됩니다.

`Consumer 설정`을 적용하면 Consumer 컨테이너만 재생성됩니다. UI는 Consumer 재생성 후 FCM Mock 설정이 유지되는지도 다시 확인합니다.

결과는 다음 파일에 누적됩니다.

- `measurement/results/pipeline_experiment.csv`
- `measurement/results/baseline.csv`

실행 로그는 `measurement/results/ui_logs/` 아래에 저장됩니다. 이 로그 디렉터리는 git에 커밋하지 않습니다.

## 스크립트 직접 실행

### 순차 기준선

Kafka를 거치지 않고 FCM Mock을 N번 직접 호출합니다.

```bash
python3 measurement/scripts/run_baseline.py --count 1000
```

기본 결과 파일:

```text
measurement/results/baseline.csv
```

### Kafka 파이프라인

Producer -> Kafka -> Consumer -> FCM Mock 전체 경로를 측정합니다.

```bash
python3 measurement/scripts/run_pipeline_experiment.py --count 1000
```

스크립트는 실행 전 `letter.id <= count` 범위의 `arrival_date`를 `CURDATE()`로 업데이트합니다. 그래서 날짜가 바뀌어도 기존 시드 데이터를 계속 사용할 수 있습니다.

기본 결과 파일:

```text
measurement/results/pipeline_experiment.csv
```

## 측정 시 주의사항

- FCM Mock 메트릭은 누적값이므로 스크립트는 실행 전/후 delta를 기록합니다.
- 설정을 바꾼 뒤에는 낮은 건수로 먼저 확인하는 것이 좋습니다.
- 반복 가능한 성능 측정에서는 FCM Mock 실패율을 `0`으로 둡니다.
- Docker Compose 기본 FCM Mock 실패율은 `0`입니다. DLT나 실패 재시도 확인이 필요할 때만 실패율을 올립니다.
- FCM 배치 Mock은 최대 500건 배치 호출 모델을 흉내냅니다. 설정한 delay는 메시지마다가 아니라 배치 요청 1회마다 적용됩니다.
- 설정 sweep은 10,000건으로 빠르게 확인하고, 최종 검증은 1,000,000건으로 수행했습니다.

## 주요 결과

### 10,000건 기준 비교

환경:

- Kafka partitions: 10
- Consumer `max.poll.records`: 500
- FCM Mock delay: 50ms
- FCM Mock failure rate: 0%
- 메시지 수: 10,000

| 시나리오 | 소요 시간 | 처리량 | 비고 |
|---|---:|---:|---|
| 순차 기준선 | 591.633초 | 16.902 msg/s | Kafka 없이 FCM Mock 직접 호출 |
| Kafka 파이프라인, FCM 단건 호출 | 589.000초 | 16.978 msg/s | Producer는 178ms에 발행했지만 Consumer가 FCM을 단건 호출 |
| Kafka 파이프라인, FCM 배치 호출 | 1.701초 | 5,879.209 msg/s | 500건 단위 배치 호출 |
| Kafka 파이프라인, FCM 배치 호출 클린 재측정 | 1.672초 | 5,981.840 msg/s | DLT 토픽 정리 후 실패율 0% |
| Kafka 파이프라인, 실패율 2% | 1.698초 | 5,888.581 msg/s | 10,000건 중 202건 실패, DLT 경로 확인용 |

Kafka를 붙인 것만으로는 성능이 좋아지지 않았습니다. 병목은 Consumer가 FCM을 단건 HTTP 호출로 보내는 부분이었고, FCM 배치 호출로 바꾼 뒤 10,000건 처리 시간이 약 589초에서 약 1.7초로 줄었습니다.

### FCM 지연 실험

환경:

- Kafka partitions: 10
- Consumer `max.poll.records`: 500
- FCM Mock failure rate: 0%
- 메시지 수: 10,000

| FCM Mock 지연 | 소요 시간 | 처리량 |
|---:|---:|---:|
| 20ms | 1.152초 | 8,682.134 msg/s |
| 50ms | 1.641초 | 6,095.309 msg/s |
| 100ms | 2.655초 | 3,767.124 msg/s |

FCM 배치 호출 지연이 커질수록 처리량이 낮아졌습니다. 이 결과는 Kafka 발행보다 Consumer-FCM 배치 호출 구간이 전체 시간에 더 큰 영향을 준다는 점을 보여줍니다.

### Consumer 배치 크기 실험

환경:

- Kafka partitions: 10
- FCM Mock delay: 50ms
- FCM Mock failure rate: 0%
- 메시지 수: 10,000

| Consumer `max.poll.records` | 소요 시간 | 처리량 |
|---:|---:|---:|
| 100 | 6.226초 | 1,606.088 msg/s |
| 200 | 3.675초 | 2,721.415 msg/s |
| 500 | 1.653초 | 6,048.868 msg/s |

`max.poll.records`가 커질수록 FCM 배치 호출 횟수가 줄어 처리량이 좋아졌습니다. 현재 FCM Mock 배치 한도 500건과 맞는 `500`이 가장 좋은 결과를 보였습니다.

### 100만 건 최종 측정

환경:

- Kafka partitions: 10
- Consumer `max.poll.records`: 500
- FCM Mock delay: 50ms
- FCM Mock failure rate: 0%
- 메시지 수: 1,000,000

| 요청 | 발행 | FCM 성공 | FCM 실패 | Producer 발행 시간 | E2E 전체 시간 | 처리량 |
|---:|---:|---:|---:|---:|---:|---:|
| 1,000,000 | 1,000,000 | 1,000,000 | 0 | 4,477ms | 123.466초 | 8,099.365 msg/s |

100만 건 전체가 실패 없이 처리됐습니다. Producer 발행 시간은 약 4.5초였고, E2E 시간은 약 123초였습니다.

## 파티션 수 실험에 대한 판단

현재 로컬 구성은 Kafka 파티션 10개와 Consumer 애플리케이션 1개입니다. 파티션 수를 늘려도 Consumer 인스턴스나 listener concurrency가 같이 늘어나지 않으면 처리량이 자동으로 증가하지 않습니다.

이번 결과에서는 Producer/Kafka보다 Consumer-FCM 배치 호출 구간이 병목으로 확인됐습니다. 따라서 파티션 수만 바꾸는 실험은 필수로 보지 않았습니다. 다음 확장 실험을 한다면 Consumer 인스턴스 수나 listener concurrency를 함께 늘려 수평 확장 효과를 측정하는 편이 더 의미 있습니다.
