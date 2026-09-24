// ==================== Reports Module ====================
// Report state
let currentReport = null;

export const getCurrentReport = () => currentReport;

export const setCurrentReport = (title, columns, rows) => {
  currentReport = {
    title,
    columns,
    rows,
  };
};

export const renderReportTable = (columns, rows) => {
  const reportOutput = document.getElementById('report-output');
  if (!reportOutput) return;
  
  reportOutput.innerHTML = '';
  if (!rows.length) {
    reportOutput.textContent = 'Không có kết quả.';
    return;
  }
  
  const renderTable = (cols, tableRows) => {
    const table = document.createElement('table');
    table.className = 'data-table';
    const thead = document.createElement('thead');
    const headerRow = document.createElement('tr');
    cols.forEach((col) => {
      const th = document.createElement('th');
      th.textContent = col;
      headerRow.appendChild(th);
    });
    thead.appendChild(headerRow);
    table.appendChild(thead);
    const tbody = document.createElement('tbody');
    tableRows.forEach((row) => {
      const tr = document.createElement('tr');
      cols.forEach((col) => {
        const td = document.createElement('td');
        td.textContent = row[col] ?? '';
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    return table;
  };
  
  const wrap = document.createElement('div');
  wrap.className = 'table-wrap';
  wrap.appendChild(renderTable(columns, rows));
  reportOutput.appendChild(wrap);
};

export const setActiveReportButton = (button) => {
  const reportButtons = [
    document.getElementById('engineer-report-btn'),
    document.getElementById('requester-report-btn'),
    document.getElementById('backlog-report-btn'),
    document.getElementById('sla-report-btn')
  ].filter(Boolean);
  
  reportButtons.forEach((btn) => btn.classList.remove('active'));
  if (button) {
    button.classList.add('active');
  }
};

export const downloadReportCsv = () => {
  if (!currentReport || !currentReport.rows.length) {
    alert('Hãy chạy báo cáo trước khi tải xuống.');
    return;
  }
  const escapeCsv = (value) => {
    if (value === null || value === undefined) return '';
    const str = String(value);
    if (/[",\n]/.test(str)) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  };
  const lines = [];
  lines.push(currentReport.columns.map(escapeCsv).join(','));
  currentReport.rows.forEach((row) => {
    const line = currentReport.columns.map((col) => escapeCsv(row[col]));
    lines.push(line.join(','));
  });
  const blob = new Blob([lines.join('\n')], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const stamp = new Date().toISOString().slice(0, 10);
  link.href = url;
  link.download = `${currentReport.title || 'report'}-${stamp}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};
