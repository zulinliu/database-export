/* ====== Global State ====== */
let currentPage = 'export';
let currentMode = 'customTables';
let connected = false;
let dbTables = [];
let selectedTables = new Set();
let currentTaskId = null;
let progressInterval = null;
let poolInterval = null;
let templates = [];

/* ====== Init ====== */
document.addEventListener('DOMContentLoaded', () => {
    const savedTheme = localStorage.getItem('theme') || 'light';
    setTheme(savedTheme);
    toggleTimeFilter();
    toggleFieldFilter();
    checkLogin();
    loadExportFiles();
});

/* ====== Theme ====== */
document.getElementById('themeToggle').addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-theme');
    setTheme(current === 'dark' ? 'light' : 'dark');
});

function setTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
    document.getElementById('themeToggle').textContent = theme === 'dark' ? '☀️' : '🌙';
}

/* ====== Auth ====== */
function checkLogin() {
    fetch('/api/checkLogin').then(r => r.json()).then(data => {
        if (!data.success) { window.location.href = '/login.html'; return; }
        document.getElementById('currentUser').textContent = data.data;
    }).catch(() => { window.location.href = '/login.html'; });
}

function logout() {
    fetch('/api/logout', {method: 'POST'}).then(() => { window.location.href = '/login.html'; });
}

/* ====== Page Navigation ====== */
function switchPage(page) {
    currentPage = page;
    document.querySelectorAll('.nav-tabs .nav-tab').forEach(t => t.classList.toggle('active', t.dataset.page === page));
    document.querySelectorAll('.page-container').forEach(p => p.style.display = 'none');
    document.getElementById('page-' + page).style.display = '';
    if (page === 'templates') loadTemplates();
    if (page === 'records') loadExportFiles();
}

/* ====== Export Mode Switch ====== */
function switchMode(btn, mode) {
    currentMode = mode;
    btn.parentElement.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('mode-customTables').style.display = mode === 'customTables' ? '' : 'none';
    document.getElementById('mode-selectTables').style.display = mode === 'selectTables' ? '' : 'none';
    document.getElementById('mode-customSql').style.display = mode === 'customSql' ? '' : 'none';
    if (mode === 'selectTables' && connected) loadTableList();
}

/* ====== DB Type Change ====== */
function onDbTypeChange() {
    const type = document.getElementById('dbType').value;
    document.getElementById('driverCustomRow').style.display = type === 'custom' ? '' : 'none';
    const port = document.getElementById('dbPort');
    if (type === 'dm') port.value = 5236;
    else if (type === 'mysql') port.value = 3306;
}

/* ====== Connection ====== */
function getDbInfo() {
    return {
        type: document.getElementById('dbType').value,
        host: document.getElementById('dbHost').value,
        port: parseInt(document.getElementById('dbPort').value),
        databaseName: document.getElementById('dbName').value,
        username: document.getElementById('dbUser').value,
        password: document.getElementById('dbPass').value,
        driverClass: document.getElementById('dbDriver').value,
        url: document.getElementById('dbUrl').value
    };
}

async function testConnection() {
    const body = document.getElementById('connectionTestBody');
    body.innerHTML = '<div class="loading-spinner" style="width:36px;height:36px;margin:20px auto;"></div><p style="margin-top:12px;">正在测试连接...</p>';
    openModal('connectionTestModal');
    try {
        const res = await fetch('/api/database/connect', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(getDbInfo())
        });
        const data = await res.json();
        if (data.success) {
            const db = getDbInfo();
            body.innerHTML = `
                <div style="font-size:48px;color:var(--success);">✓</div>
                <h3 style="margin:12px 0;color:var(--success);">连接成功！</h3>
                <div style="text-align:left;background:var(--bg-input);padding:16px;border-radius:8px;font-size:14px;">
                    <p>数据库类型：${escapeHtml(db.type === 'dm' ? '达梦 DM' : db.type === 'mysql' ? 'MySQL' : '自定义')}</p>
                    <p>服务器：${escapeHtml(db.host)}:${escapeHtml(db.port)}</p>
                    <p>数据库名：${escapeHtml(db.databaseName)}</p>
                </div>`;
        } else {
            body.innerHTML = `
                <div style="font-size:48px;color:var(--error);">✗</div>
                <h3 style="margin:12px 0;color:var(--error);">连接失败</h3>
                <div style="text-align:left;background:var(--bg-input);padding:16px;border-radius:8px;font-size:14px;">
                    <p>${escapeHtml(data.message)}</p>
                    <p style="margin-top:8px;color:var(--text-secondary);">请检查：</p>
                    <ul style="padding-left:20px;color:var(--text-secondary);"><li>主机地址和端口是否正确</li><li>数据库服务是否已启动</li><li>防火墙是否放行对应端口</li></ul>
                </div>`;
        }
    } catch (e) {
        body.innerHTML = `<div style="font-size:48px;color:var(--error);">✗</div><h3 style="margin:12px 0;color:var(--error);">连接失败</h3><p>网络错误: ${escapeHtml(e.message)}</p>`;
    }
}

async function connectDatabase() {
    try {
        const res = await fetch('/api/database/connect', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(getDbInfo())
        });
        const data = await res.json();
        if (data.success) {
            connected = true;
            document.getElementById('connDot').classList.add('connected');
            document.getElementById('connText').textContent = document.getElementById('dbName').value || '已连接';
            showToast('success', '连接成功', '数据库连接已建立');
            loadTableList();
            startPoolMonitor();
        } else {
            showToast('error', '连接失败', data.message);
        }
    } catch (e) {
        showToast('error', '连接失败', e.message);
    }
}

async function loadTableList() {
    try {
        const res = await fetch('/api/database/tables');
        const data = await res.json();
        if (data.success) {
            dbTables = data.data;
            renderTableList();
        }
    } catch (e) { console.error(e); }
}

function renderTableList(filter) {
    const container = document.getElementById('tableList');
    let tables = dbTables;
    if (filter) tables = tables.filter(t => t.tableName.toLowerCase().includes(filter.toLowerCase()));
    if (tables.length === 0) {
        container.innerHTML = '<p style="color:var(--text-muted);text-align:center;padding:40px;">无匹配的表</p>';
        return;
    }
    let html = '<table class="data-table"><thead><tr><th style="width:40px;"></th><th>表名</th><th>记录数</th></tr></thead><tbody>';
    tables.forEach(t => {
        const checked = selectedTables.has(t.tableName) ? 'checked' : '';
        html += `<tr><td><input type="checkbox" class="checkbox" ${checked} data-table="${escapeAttr(t.tableName)}" onchange="toggleTable('${escapeAttr(t.tableName)}', this.checked)"></td>
                 <td>${escapeHtml(t.tableName)}</td><td>${t.rowCount >= 0 ? t.rowCount.toLocaleString() : '-'}</td></tr>`;
    });
    html += '</tbody></table>';
    container.innerHTML = html;
    updateSelectedCount();
}

function toggleTable(name, checked) {
    if (checked) selectedTables.add(name); else selectedTables.delete(name);
    updateSelectedCount();
}
function selectAllTables() { dbTables.forEach(t => selectedTables.add(t.tableName)); renderTableList(document.getElementById('tableSearch').value); }
function invertTables() { dbTables.forEach(t => { if (selectedTables.has(t.tableName)) selectedTables.delete(t.tableName); else selectedTables.add(t.tableName); }); renderTableList(document.getElementById('tableSearch').value); }
function clearTables() { selectedTables.clear(); renderTableList(); }
function filterTables() { renderTableList(document.getElementById('tableSearch').value); }
function updateSelectedCount() { document.getElementById('selectedCount').textContent = '已选择: ' + selectedTables.size + ' 个表'; }

/* ====== Filter Toggles ====== */
function toggleTimeFilter() {
    document.getElementById('timeFilterFields').style.display = document.getElementById('enableTimeFilter').checked ? 'flex' : 'none';
}
function toggleFieldFilter() {
    document.getElementById('fieldFilterFields').style.display = document.getElementById('enableFieldFilter').checked ? 'flex' : 'none';
}

/* ====== SQL Validate ====== */
async function validateSql() {
    const sql = document.getElementById('customSql').value;
    const resultDiv = document.getElementById('sqlValidateResult');
    try {
        const res = await fetch('/api/sql/validate', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({sql})
        });
        const data = await res.json();
        if (data.success && data.data.details) {
            let html = '';
            data.data.details.forEach(d => {
                const icon = d.valid ? '✓' : '✗';
                const color = d.valid ? 'var(--success)' : 'var(--error)';
                html += `<div style="color:${color};margin:4px 0;">${icon} 第${d.line}行: ${escapeHtml(d.tableName)} - ${escapeHtml(d.message)}</div>`;
            });
            resultDiv.innerHTML = html;
        }
    } catch (e) { resultDiv.innerHTML = '<div style="color:var(--error);">验证失败: ' + e.message + '</div>'; }
}

/* ====== Export ====== */
function buildExportConfig() {
    const format = document.querySelector('input[name="exportFormat"]:checked').value;
    const config = {
        exportType: currentMode,
        tables: currentMode === 'customTables' ? document.getElementById('tableNames').value : '',
        customSql: currentMode === 'customSql' ? document.getElementById('customSql').value : '',
        enableTimeFilter: document.getElementById('enableTimeFilter').checked,
        timeFieldNames: document.getElementById('timeFieldNames').value,
        startDate: document.getElementById('startDate').value,
        endDate: document.getElementById('endDate').value,
        enableFieldFilter: document.getElementById('enableFieldFilter').checked,
        fieldFilter: null,
        exportFormats: format,
        maxConnections: parseInt(document.getElementById('maxConnections').value) || 5
    };

    if (currentMode === 'selectTables') {
        config.tables = Array.from(selectedTables).join(',');
        config.exportType = 'customTables';
    }

    if (config.enableFieldFilter) {
        config.fieldFilter = {
            fieldName: document.getElementById('filterFieldName').value,
            filterType: document.getElementById('filterType').value,
            filterValue: document.getElementById('filterValue').value
        };
    }
    return config;
}

async function startExport() {
    if (!connected) { showToast('error', '导出失败', '请先连接数据库'); return; }

    const config = buildExportConfig();
    if (config.exportType === 'customTables' && !config.tables.trim()) {
        showToast('error', '导出失败', '请输入表名'); return;
    }
    if (config.exportType === 'customSql' && !config.customSql.trim()) {
        showToast('error', '导出失败', '请输入SQL语句'); return;
    }

    try {
        const res = await fetch('/api/export/start', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(config)
        });
        const data = await res.json();
        if (data.success) {
            currentTaskId = data.data;
            document.getElementById('progressSection').style.display = '';
            document.getElementById('progressBar').style.width = '0%';
            document.getElementById('logPanel').innerHTML = '';
            startProgressMonitor();
            showToast('success', '导出已启动', '任务ID: ' + currentTaskId);
        } else {
            showToast('error', '导出失败', data.message);
        }
    } catch (e) { showToast('error', '导出失败', e.message); }
}

function startProgressMonitor() {
    if (progressInterval) clearInterval(progressInterval);
    progressInterval = setInterval(async () => {
        if (!currentTaskId) return;
        try {
            const res = await fetch('/api/export/progress/' + currentTaskId);
            const data = await res.json();
            if (!data.success) return;
            const p = data.data;
            document.getElementById('progressBar').style.width = p.progressPercent + '%';
            document.getElementById('progressPercent').textContent = p.progressPercent + '%';
            document.getElementById('currentTableName').textContent = p.currentTable || '-';
            document.getElementById('completedInfo').textContent = p.completedTables + '/' + p.totalTables;
            document.getElementById('totalRows').textContent = p.totalRows.toLocaleString();
            document.getElementById('elapsedTime').textContent = formatTime(p.elapsedTime);
            document.getElementById('remainingTime').textContent = formatTime(p.estimatedRemaining);

            const logPanel = document.getElementById('logPanel');
            const prevLogCount = logPanel.children.length;
            if (p.logs && p.logs.length > prevLogCount) {
                for (let i = prevLogCount; i < p.logs.length; i++) {
                    const l = p.logs[i];
                    const time = new Date(l.timestamp).toLocaleTimeString();
                    const entry = document.createElement('div');
                    entry.className = 'log-entry';
                    const timeSpan = document.createElement('span');
                    timeSpan.className = 'log-time';
                    timeSpan.textContent = '[' + time + ']';
                    const levelSpan = document.createElement('span');
                    levelSpan.className = 'log-level ' + l.level;
                    levelSpan.textContent = '[' + l.level + ']';
                    const msgSpan = document.createElement('span');
                    msgSpan.className = 'log-msg';
                    msgSpan.textContent = l.message;
                    entry.appendChild(timeSpan);
                    entry.appendChild(levelSpan);
                    entry.appendChild(msgSpan);
                    logPanel.appendChild(entry);
                }
                logPanel.scrollTop = logPanel.scrollHeight;
            }

            if (p.status === 'COMPLETED' || p.status === 'FAILED' || p.status === 'CANCELLED') {
                clearInterval(progressInterval);
                progressInterval = null;
                document.getElementById('cancelBtn').style.display = 'none';
                if (p.status === 'COMPLETED') {
                    showExportResult(p);
                    showToast('success', '导出完成', '文件: ' + p.fileName);
                } else if (p.status === 'FAILED') {
                    showToast('error', '导出失败', p.errorMessage || '未知错误');
                } else {
                    showToast('warning', '导出已取消', '');
                }
                loadExportFiles();
            }
        } catch (e) { console.error(e); }
    }, 1000);
}

async function cancelExport() {
    if (!currentTaskId) return;
    const res = await fetch('/api/export/cancel/' + currentTaskId, {method: 'POST'});
    const data = await res.json();
    showToast(data.success ? 'info' : 'error', data.success ? '取消请求已发送' : '取消失败', '');
}

function showExportResult(p) {
    const body = document.getElementById('exportResultBody');
    body.innerHTML = `
        <div style="font-size:48px;color:var(--success);">✓</div>
        <h3 style="margin:12px 0;color:var(--success);">导出成功！</h3>
        <div style="text-align:left;background:var(--bg-input);padding:16px;border-radius:8px;font-size:14px;line-height:2;">
            <p>导出表数：<strong>${escapeHtml(p.totalTables)}</strong> 个</p>
            <p>成功：<strong style="color:var(--success);">${escapeHtml(p.successCount)}</strong> 个</p>
            <p>失败：<strong style="color:${p.failCount > 0 ? 'var(--error)' : 'var(--text-primary)'};">${escapeHtml(p.failCount)}</strong> 个</p>
            <p>总数据量：<strong>${escapeHtml(p.totalRows.toLocaleString())}</strong> 条</p>
            <p>文件大小：<strong>${(p.fileSize / 1024 / 1024).toFixed(1)}</strong> MB</p>
            <p>耗时：<strong>${formatTime(p.elapsedTime)}</strong></p>
            <p style="margin-top:8px;word-break:break-all;">文件名：${escapeHtml(p.fileName)}</p>
        </div>`;
    document.getElementById('downloadResultBtn').onclick = () => downloadFile(p.fileName);
    openModal('exportResultModal');
}

/* ====== Connection Pool Monitor ====== */
function startPoolMonitor() {
    if (poolInterval) clearInterval(poolInterval);
    document.getElementById('poolSection').style.display = '';
    updatePoolStatus();
    poolInterval = setInterval(updatePoolStatus, 5000);
}

async function updatePoolStatus() {
    try {
        const res = await fetch('/api/database/status');
        const data = await res.json();
        if (!data.success) return;
        const s = data.data;
        const max = s.maxConnections || 10;
        document.getElementById('poolActive').style.width = (s.activeConnections / max * 100) + '%';
        document.getElementById('poolActiveVal').textContent = s.activeConnections;
        document.getElementById('poolIdle').style.width = (s.idleConnections / max * 100) + '%';
        document.getElementById('poolIdleVal').textContent = s.idleConnections;
        document.getElementById('poolWait').style.width = (s.threadsAwaiting > 0 ? Math.min(s.threadsAwaiting / max * 100, 100) : 0) + '%';
        document.getElementById('poolWaitVal').textContent = s.threadsAwaiting;
        document.getElementById('poolTotal').textContent = s.totalConnections;
        document.getElementById('poolMax').textContent = max;
        document.getElementById('poolStatus').textContent = s.status;
        document.getElementById('poolStatus').style.color = s.status === '正常' ? 'var(--success)' : 'var(--error)';
    } catch (e) {}
}

/* ====== Templates ====== */
async function loadTemplates() {
    try {
        const keyword = document.getElementById('templateSearch').value;
        const url = keyword ? '/api/template/list?keyword=' + encodeURIComponent(keyword) : '/api/template/list';
        const res = await fetch(url);
        const data = await res.json();
        if (data.success) {
            templates = data.data;
            renderTemplates();
        }
    } catch (e) { console.error(e); }
}
function searchTemplates() { loadTemplates(); }

function renderTemplates() {
    const tbody = document.getElementById('templateTableBody');
    const empty = document.getElementById('templateEmptyState');
    if (templates.length === 0) {
        tbody.innerHTML = '';
        empty.style.display = '';
        return;
    }
    empty.style.display = 'none';
    tbody.innerHTML = templates.map(t => `
        <tr>
            <td><input type="checkbox" class="checkbox template-check" data-id="${t.id}"></td>
            <td>${escapeHtml(t.name)}</td>
            <td style="color:var(--text-secondary);">${escapeHtml(t.description || '-')}</td>
            <td>${t.createTime ? new Date(t.createTime).toLocaleString() : '-'}</td>
            <td>
                <button class="btn btn-sm btn-secondary" onclick="loadTemplateConfig(${t.id})">加载</button>
                <button class="btn btn-sm btn-danger" onclick="deleteTemplate(${t.id}, '${escapeAttr(t.name)}')">删除</button>
            </td>
        </tr>`).join('');
}

function toggleAllTemplates() {
    const checked = document.getElementById('templateCheckAll').checked;
    document.querySelectorAll('.template-check').forEach(c => c.checked = checked);
}

function getSelectedTemplateIds() {
    return Array.from(document.querySelectorAll('.template-check:checked')).map(c => parseInt(c.dataset.id));
}

function openSaveTemplateModal() { openModal('saveTemplateModal'); }

async function saveTemplate() {
    const name = document.getElementById('templateName').value.trim();
    const desc = document.getElementById('templateDesc').value.trim();
    if (!name) { showToast('error', '保存失败', '请输入模板名称'); return; }

    const config = buildExportConfig();
    try {
        const res = await fetch('/api/template/save', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({name, description: desc, configJson: JSON.stringify(config)})
        });
        const data = await res.json();
        if (data.success) {
            showToast('success', '模板保存成功', '模板"' + name + '"已保存');
            closeModal('saveTemplateModal');
            document.getElementById('templateName').value = '';
            document.getElementById('templateDesc').value = '';
        } else {
            showToast('error', '保存失败', data.message);
        }
    } catch (e) { showToast('error', '保存失败', e.message); }
}

function openNewTemplateModal() {
    document.getElementById('templateName').value = '';
    document.getElementById('templateDesc').value = '';
    openModal('saveTemplateModal');
}

async function deleteTemplate(id, name) {
    showConfirm('确认删除', `确定要删除模板"${name}"吗？此操作不可撤销。`, async () => {
        const res = await fetch('/api/template/' + id, {method: 'DELETE'});
        const data = await res.json();
        if (data.success) { showToast('success', '删除成功', ''); loadTemplates(); }
        else showToast('error', '删除失败', data.message);
    });
}

async function batchDeleteTemplates() {
    const ids = getSelectedTemplateIds();
    if (ids.length === 0) { showToast('warning', '请选择', '请先选择要删除的模板'); return; }
    showConfirm('确认删除', `确定要删除选中的 ${ids.length} 个模板吗？`, async () => {
        const res = await fetch('/api/template/batch-delete', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ids})
        });
        const data = await res.json();
        if (data.success) { showToast('success', '删除成功', data.message); loadTemplates(); }
    });
}

async function batchExportTemplates() {
    const ids = getSelectedTemplateIds();
    if (ids.length === 0) { showToast('warning', '请选择', '请先选择要导出的模板'); return; }
    try {
        const res = await fetch('/api/template/export', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ids})
        });
        if (res.ok) {
            const blob = await res.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a'); a.href = url; a.download = 'templates_export.zip';
            a.click(); URL.revokeObjectURL(url);
            showToast('success', '导出成功', '');
        }
    } catch (e) { showToast('error', '导出失败', e.message); }
}

async function importTemplates(event) {
    const file = event.target.files[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    try {
        const res = await fetch('/api/template/import', {method: 'POST', body: formData});
        const data = await res.json();
        if (data.success) { showToast('success', '导入成功', data.message); loadTemplates(); }
        else showToast('error', '导入失败', data.message);
    } catch (e) { showToast('error', '导入失败', e.message); }
    event.target.value = '';
}

async function loadTemplateConfig(id) {
    const t = templates.find(t => t.id === id);
    if (!t || !t.configJson) { showToast('error', '加载失败', '模板配置为空'); return; }
    try {
        const config = JSON.parse(t.configJson);
        applyConfig(config);
        switchPage('export');
        showToast('success', '模板已加载', '配置已填充到导出页面');
    } catch (e) { showToast('error', '加载失败', '配置解析失败'); }
}

async function loadSelectedTemplate() {
    const ids = getSelectedTemplateIds();
    if (ids.length !== 1) { showToast('warning', '请选择', '请选择一个模板进行加载'); return; }
    loadTemplateConfig(ids[0]);
}

function applyConfig(config) {
    if (config.exportType) {
        const modeMap = {customTables: 0, selectTables: 1, customSql: 2};
        const btns = document.querySelectorAll('#page-export .section:nth-child(2) .nav-tab');
        btns.forEach(b => b.classList.remove('active'));
        const idx = modeMap[config.exportType] || 0;
        if (btns[idx]) { btns[idx].classList.add('active'); switchMode(btns[idx], config.exportType); }
    }
    if (config.tables) document.getElementById('tableNames').value = config.tables;
    if (config.customSql) document.getElementById('customSql').value = config.customSql;
    if (config.enableTimeFilter !== undefined) {
        document.getElementById('enableTimeFilter').checked = config.enableTimeFilter;
        toggleTimeFilter();
    }
    if (config.timeFieldNames) document.getElementById('timeFieldNames').value = config.timeFieldNames;
    if (config.startDate) document.getElementById('startDate').value = config.startDate;
    if (config.endDate) document.getElementById('endDate').value = config.endDate;
    if (config.enableFieldFilter !== undefined) {
        document.getElementById('enableFieldFilter').checked = config.enableFieldFilter;
        toggleFieldFilter();
    }
    if (config.fieldFilter) {
        document.getElementById('filterFieldName').value = config.fieldFilter.fieldName || '';
        document.getElementById('filterType').value = config.fieldFilter.filterType || 'EQUAL';
        document.getElementById('filterValue').value = config.fieldFilter.filterValue || '';
    }
    if (config.exportFormats) {
        const radio = document.querySelector(`input[name="exportFormat"][value="${config.exportFormats}"]`);
        if (radio) radio.checked = true;
    }
    if (config.maxConnections) document.getElementById('maxConnections').value = config.maxConnections;
}

/* ====== Export Files / Records ====== */
async function loadExportFiles() {
    try {
        const res = await fetch('/api/export/files');
        const data = await res.json();
        if (data.success) renderExportFiles(data.data);
    } catch (e) {}
}

function renderExportFiles(files) {
    const tbody = document.getElementById('recordsTableBody');
    const empty = document.getElementById('recordsEmptyState');
    if (!files || files.length === 0) {
        tbody.innerHTML = '';
        empty.style.display = '';
        return;
    }
    empty.style.display = 'none';
    tbody.innerHTML = files.map(f => `
        <tr>
            <td><input type="checkbox" class="checkbox file-check" data-name="${escapeAttr(f.fileName)}"></td>
            <td style="word-break:break-all;">${escapeHtml(f.fileName)}</td>
            <td>${(f.fileSize / 1024 / 1024).toFixed(1)} MB</td>
            <td>${f.createTime ? new Date(f.createTime).toLocaleString() : '-'}</td>
            <td><span style="color:var(--success);">${escapeHtml(f.status)}</span></td>
            <td>
                <button class="btn btn-sm btn-primary" onclick="downloadFile('${escapeAttr(f.fileName)}')">下载</button>
                <button class="btn btn-sm btn-danger" onclick="deleteFile('${escapeAttr(f.fileName)}')">删除</button>
            </td>
        </tr>`).join('');
}

function toggleAllFiles() {
    const checked = document.getElementById('fileCheckAll').checked;
    document.querySelectorAll('.file-check').forEach(c => c.checked = checked);
}

function downloadFile(fileName) {
    window.open('/api/export/download/' + encodeURIComponent(fileName), '_blank');
}

async function deleteFile(fileName) {
    showConfirm('确认删除', `确定要删除文件"${fileName}"吗？`, async () => {
        const res = await fetch('/api/export/files/' + encodeURIComponent(fileName), {method: 'DELETE'});
        const data = await res.json();
        if (data.success) { showToast('success', '删除成功', ''); loadExportFiles(); }
        else showToast('error', '删除失败', data.message);
    });
}

async function batchDeleteFiles() {
    const names = Array.from(document.querySelectorAll('.file-check:checked')).map(c => c.dataset.name);
    if (names.length === 0) { showToast('warning', '请选择', '请先选择要删除的文件'); return; }
    showConfirm('确认删除', `确定要删除选中的 ${names.length} 个文件吗？`, async () => {
        const res = await fetch('/api/export/files/batch-delete', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({fileNames: names})
        });
        const data = await res.json();
        if (data.success) { showToast('success', '删除成功', ''); loadExportFiles(); }
    });
}

async function downloadSelectedFiles() {
    const names = Array.from(document.querySelectorAll('.file-check:checked')).map(c => c.dataset.name);
    if (names.length === 0) { showToast('warning', '请选择', '请先选择要下载的文件'); return; }
    names.forEach(n => downloadFile(n));
}

/* ====== Modal Helpers ====== */
function openModal(id) { document.getElementById(id).classList.add('active'); }
function closeModal(id) { document.getElementById(id).classList.remove('active'); }

function showConfirm(title, message, onConfirm) {
    document.getElementById('confirmTitle').textContent = title;
    document.getElementById('confirmBody').innerHTML = `
        <div style="font-size:48px;color:var(--warning);margin-bottom:16px;">?</div>
        <p style="font-size:15px;margin-bottom:8px;">${escapeHtml(message)}</p>
        <p style="font-size:13px;color:var(--text-muted);">此操作不可撤销。</p>`;
    const btn = document.getElementById('confirmAction');
    btn.onclick = () => { closeModal('confirmModal'); onConfirm(); };
    openModal('confirmModal');
}

/* ====== Toast ====== */
function showToast(type, title, message) {
    const container = document.getElementById('toastContainer');
    const icons = {success: '✓', error: '✗', warning: '⚠', info: 'ℹ'};
    const toast = document.createElement('div');
    toast.className = 'toast ' + type;
    toast.innerHTML = `<span class="toast-icon">${icons[type]||''}</span><div class="toast-content"><div class="toast-title">${escapeHtml(title)}</div>${message ? '<div class="toast-message">' + escapeHtml(message) + '</div>' : ''}</div>`;
    container.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; toast.style.transition = 'opacity 0.3s'; setTimeout(() => toast.remove(), 300); }, 3000);
}

/* ====== Utilities ====== */
function escapeHtml(str) {
    if (str == null) return '';
    const div = document.createElement('div');
    div.textContent = String(str);
    return div.innerHTML;
}

function formatTime(seconds) {
    if (!seconds || seconds < 0) return '00:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
}

function escapeAttr(str) {
    if (str == null) return '';
    return String(str).replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/'/g,'&#39;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
