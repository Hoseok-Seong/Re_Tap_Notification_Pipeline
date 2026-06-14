const elements = {
  kind: document.querySelector("#kind"),
  count: document.querySelector("#count"),
  label: document.querySelector("#label"),
  timeoutSeconds: document.querySelector("#timeoutSeconds"),
  skipPrepareArrivals: document.querySelector("#skipPrepareArrivals"),
  runButton: document.querySelector("#runButton"),
  refreshButton: document.querySelector("#refreshButton"),
  statusList: document.querySelector("#statusList"),
  logBox: document.querySelector("#logBox"),
  pipelineTable: document.querySelector("#pipelineTable"),
  baselineTable: document.querySelector("#baselineTable"),
};

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
  };

  const response = await fetch("/api/run", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await response.json();
  if (!response.ok) {
    alert(body.error || "측정 실행에 실패했습니다.");
  }
  await refresh();
});

elements.refreshButton.addEventListener("click", refresh);

async function refresh() {
  const [statusResponse, resultsResponse, logResponse] = await Promise.all([
    fetch("/api/status"),
    fetch("/api/results"),
    fetch("/api/log"),
  ]);

  const status = await statusResponse.json();
  const results = await resultsResponse.json();
  const log = await logResponse.json();

  renderStatus(status);
  renderTable(elements.pipelineTable, results.pipeline || []);
  renderTable(elements.baselineTable, results.baseline || []);
  elements.logBox.textContent = log.text || "";
  elements.runButton.disabled = Boolean(status.running);
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
