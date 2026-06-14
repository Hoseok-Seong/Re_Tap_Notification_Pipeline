const elements = {
  kind: document.querySelector("#kind"),
  count: document.querySelector("#count"),
  label: document.querySelector("#label"),
  timeoutSeconds: document.querySelector("#timeoutSeconds"),
  skipPrepareArrivals: document.querySelector("#skipPrepareArrivals"),
  applyFcmBeforeRun: document.querySelector("#applyFcmBeforeRun"),
  applyConsumerBeforeRun: document.querySelector("#applyConsumerBeforeRun"),
  fcmDelayMs: document.querySelector("#fcmDelayMs"),
  fcmFailureRatePercent: document.querySelector("#fcmFailureRatePercent"),
  maxPollRecords: document.querySelector("#maxPollRecords"),
  runButton: document.querySelector("#runButton"),
  applyFcmButton: document.querySelector("#applyFcmButton"),
  applyConsumerButton: document.querySelector("#applyConsumerButton"),
  refreshButton: document.querySelector("#refreshButton"),
  fcmMetrics: document.querySelector("#fcmMetrics"),
  consumerMetrics: document.querySelector("#consumerMetrics"),
  statusList: document.querySelector("#statusList"),
  logBox: document.querySelector("#logBox"),
  pipelineTable: document.querySelector("#pipelineTable"),
  baselineTable: document.querySelector("#baselineTable"),
};

let fcmInputsDirty = false;
let consumerInputsDirty = false;

elements.kind.addEventListener("change", () => {
  const count = elements.count.value || "10000";
  elements.label.value = elements.kind.value === "baseline"
    ? `baseline-${count}`
    : `pipeline-batch-${count}`;
});

elements.count.addEventListener("change", () => {
  const prefix = elements.kind.value === "baseline" ? "baseline" : "pipeline-batch";
  elements.label.value = `${prefix}-${elements.count.value}`;
});

elements.runButton.addEventListener("click", async () => {
  elements.runButton.disabled = true;
  const payload = {
    kind: elements.kind.value,
    count: Number(elements.count.value),
    label: elements.label.value,
    timeoutSeconds: Number(elements.timeoutSeconds.value),
    skipPrepareArrivals: elements.skipPrepareArrivals.checked,
    applyFcmBeforeRun: elements.applyFcmBeforeRun.checked,
    applyConsumerBeforeRun: elements.applyConsumerBeforeRun.checked,
    delayMs: Number(elements.fcmDelayMs.value),
    failureRatePercent: Number(elements.fcmFailureRatePercent.value),
    maxPollRecords: Number(elements.maxPollRecords.value),
  };

  const response = await fetch("/api/run", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await response.json();
  if (!response.ok) {
    alert(body.error || "측정 실행에 실패했습니다.");
  } else {
    fcmInputsDirty = false;
    consumerInputsDirty = false;
  }
  await refresh();
});

elements.fcmDelayMs.addEventListener("input", () => {
  fcmInputsDirty = true;
});

elements.fcmFailureRatePercent.addEventListener("input", () => {
  fcmInputsDirty = true;
});

elements.maxPollRecords.addEventListener("input", () => {
  consumerInputsDirty = true;
});

elements.applyFcmButton.addEventListener("click", async () => {
  elements.applyFcmButton.disabled = true;
  const payload = {
    delayMs: Number(elements.fcmDelayMs.value),
    failureRatePercent: Number(elements.fcmFailureRatePercent.value),
  };

  const response = await fetch("/api/fcm-config", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await response.json();
  if (!response.ok) {
    alert(body.error || "FCM Mock 설정 적용에 실패했습니다.");
  } else {
    fcmInputsDirty = false;
  }
  await refresh();
});

elements.applyConsumerButton.addEventListener("click", async () => {
  elements.applyConsumerButton.disabled = true;
  const payload = {
    maxPollRecords: Number(elements.maxPollRecords.value),
  };

  const response = await fetch("/api/consumer-config", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await response.json();
  if (!response.ok) {
    alert(body.error || "Consumer 설정 적용에 실패했습니다.");
  } else {
    consumerInputsDirty = false;
  }
  await refresh();
});

elements.refreshButton.addEventListener("click", refresh);

async function refresh() {
  const [statusResponse, resultsResponse, logResponse, fcmResponse, consumerResponse] = await Promise.all([
    fetch("/api/status"),
    fetch("/api/results"),
    fetch("/api/log"),
    fetch("/api/fcm-config"),
    fetch("/api/consumer-config"),
  ]);

  const status = await statusResponse.json();
  const results = await resultsResponse.json();
  const log = await logResponse.json();
  const fcm = await fcmResponse.json();
  const consumer = await consumerResponse.json();

  renderStatus(status);
  renderFcmMetrics(fcm.metrics);
  renderConsumerConfig(consumer.config);
  renderTable(elements.pipelineTable, results.pipeline || []);
  renderTable(elements.baselineTable, results.baseline || []);
  elements.logBox.textContent = log.text || "";
  elements.runButton.disabled = Boolean(status.running);
  elements.applyFcmButton.disabled = Boolean(status.running);
  elements.applyConsumerButton.disabled = Boolean(status.running);
}

function renderStatus(status) {
  const rows = [
    ["실행 중", status.running ? "yes" : "no"],
    ["종류", status.kind || "-"],
    ["라벨", status.label || "-"],
    ["시작", status.started_at || "-"],
    ["완료", status.completed_at || "-"],
    ["결과 코드", status.return_code ?? "-"],
    ["로그", status.log_file || "-"],
  ];

  elements.statusList.replaceChildren(...rows.flatMap(([key, value]) => {
    const term = document.createElement("dt");
    term.textContent = key;
    const description = document.createElement("dd");
    description.textContent = value;
    return [term, description];
  }));
}

function renderFcmMetrics(metrics) {
  if (!metrics) {
    elements.fcmMetrics.textContent = "FCM Mock 메트릭을 불러오지 못했습니다.";
    return;
  }

  elements.fcmMetrics.textContent = [
    `delay=${metrics.responseDelayMs}ms`,
    `failure=${metrics.failureRatePercent}%`,
    `total=${metrics.totalRequests}`,
    `success=${metrics.successRequests}`,
    `failureCount=${metrics.failureRequests}`,
  ].join(" / ");

  if (!fcmInputsDirty && document.activeElement !== elements.fcmDelayMs) {
    elements.fcmDelayMs.value = metrics.responseDelayMs;
  }
  if (!fcmInputsDirty && document.activeElement !== elements.fcmFailureRatePercent) {
    elements.fcmFailureRatePercent.value = metrics.failureRatePercent;
  }
}

function renderConsumerConfig(config) {
  if (!config) {
    elements.consumerMetrics.textContent = "Consumer 설정을 불러오지 못했습니다.";
    return;
  }

  const maxPollRecords = config.maxPollRecords ?? "-";
  const statusText = config.status?.status || config.status?.available || "-";
  elements.consumerMetrics.textContent = `max.poll.records=${maxPollRecords} / status=${statusText}`;

  if (!consumerInputsDirty && document.activeElement !== elements.maxPollRecords && config.maxPollRecords) {
    elements.maxPollRecords.value = config.maxPollRecords;
  }
}

function renderTable(container, rows) {
  if (!rows.length) {
    container.textContent = "아직 결과가 없습니다.";
    return;
  }

  const table = document.createElement("table");
  const headers = Object.keys(rows[0]);
  const thead = document.createElement("thead");
  const headerRow = document.createElement("tr");
  for (const header of headers) {
    const th = document.createElement("th");
    th.textContent = header;
    headerRow.append(th);
  }
  thead.append(headerRow);

  const tbody = document.createElement("tbody");
  for (const row of rows.slice().reverse()) {
    const tr = document.createElement("tr");
    for (const header of headers) {
      const td = document.createElement("td");
      td.textContent = row[header] ?? "";
      tr.append(td);
    }
    tbody.append(tr);
  }

  table.append(thead, tbody);
  container.replaceChildren(table);
}

setInterval(refresh, 2000);
refresh();
