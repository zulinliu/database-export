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
let customDriverInfo = null;

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
const themeToggleBtn = document.getElementById('themeToggle');
if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', toggleTheme);
}

const logoutBtn = document.getElementById('logoutBtn');
if (logoutBtn) {
    logoutBtn.addEventListener('click', logout);
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    setTheme(current === 'dark' ? 'light' : 'dark');
}

function setTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
    const icon = document.getElementById('themeIcon');
    if (icon) icon.textContent = theme === 'dark' ? '☀️' : '🌙';
    if (themeToggleBtn) {
        themeToggleBtn.title = theme === 'dark' ? '切换到浅色主题' : '切换到深色主题';
        themeToggleBtn.setAttribute('aria-label', themeToggleBtn.title);
    }
}

/* ====== Auth ====== */
function checkLogin() {
    fetch('/api/checkLogin').then(r => r.json()).then(data => {
        if (!data.success) { window.location.href = '/login.html'; return; }
        document.getElementById('currentUser').textContent = data.data;
    }).catch(() => { window.location.href = '/login.html'; });
}

async function logout() {
    if (logoutBtn) {
        logoutBtn.disabled = true;
        logoutBtn.textContent = '退出中...';
    }
    try {
        await fetch('/api/logout', {method: 'POST', credentials: 'same-origin'});
    } catch (e) {
        console.error(e);
    } finally {
        window.location.href = '/login.html';
    }
}

/* ====== Page Navigation ====== */
function switchPage(page) {
    currentPage = page;
    document.querySelectorAll('.sidebar-item').forEach(t => t.classList.toggle('active', t.dataset.page === page));
    document.querySelectorAll('.page-container').forEach(p => p.style.display = 'none');
    document.getElementById('page-' + page).style.display = '';
    if (page === 'templates') loadTemplates();
    if (page === 'records') loadExportFiles();
}

/* ====== Export Mode Switch ====== */
function switchMode(btn, mode) {
    currentMode = mode;
    btn.parentElement.querySelectorAll('.mode-tab').forEach(t => t.classList.remove('active'));
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
    if (type !== 'custom') {
        document.getElementById('detectedDbType').value = type;
        document.getElementById('detectedDialect').value = type;
    } else if (customDriverInfo && customDriverInfo.defaultPort) {
        port.value = customDriverInfo.defaultPort;
    }
}

/* ====== Connection ====== */
function getDbInfo() {
    const type = document.getElementById('dbType').value;
    const isCustom = type === 'custom';
    return {
        type,
        host: document.getElementById('dbHost').value,
        port: parseInt(document.getElementById('dbPort').value),
        databaseName: document.getElementById('dbName').value,
        username: document.getElementById('dbUser').value,
        password: document.getElementById('dbPass').value,
        customDriverId: isCustom ? document.getElementById('customDriverId').value : '',
        detectedType: isCustom ? document.getElementById('detectedDbType').value : type,
        dialect: isCustom ? document.getElementById('detectedDialect').value : type
    };
}

function validateDbInfo(dbInfo) {
    if (dbInfo.type === 'custom' && !dbInfo.customDriverId) {
        return '请先上传数据库 JDBC 驱动包';
    }
    if (dbInfo.type === 'custom' && (!dbInfo.dialect || dbInfo.dialect === 'custom')) {
        return '当前驱动包未识别出支持的数据库类型，无法仅凭连接信息生成 JDBC URL';
    }
    if (!dbInfo.host || !dbInfo.port || !dbInfo.databaseName || !dbInfo.username) {
        return '请完整填写主机地址、端口、数据库名和用户名';
    }
    return '';
}

async function uploadCustomDriver(event) {
    const file = event.target.files[0];
    if (!file) return;

    const fileNameEl = document.getElementById('driverFileName');
    const statusEl = document.getElementById('driverUploadStatus');
    fileNameEl.textContent = file.name;
    statusEl.className = 'driver-upload-status';
    statusEl.textContent = '正在上传并识别驱动包...';

    if (!file.name.toLowerCase().endsWith('.jar')) {
        customDriverInfo = null;
        document.getElementById('customDriverId').value = '';
        statusEl.className = 'driver-upload-status error';
        statusEl.textContent = '仅支持上传 .jar 格式的 JDBC 驱动包。';
        showToast('error', '驱动上传失败', '仅支持 .jar 文件');
        event.target.value = '';
        return;
    }

    const formData = new FormData();
    formData.append('file', file);
    try {
        const res = await fetch('/api/database/driver/upload', { method: 'POST', body: formData });
        const data = await res.json();
        if (!data.success) {
            throw new Error(data.message || '驱动上传失败');
        }
        customDriverInfo = data.data;
        document.getElementById('customDriverId').value = customDriverInfo.id || '';
        document.getElementById('detectedDbType').value = customDriverInfo.detectedType || '';
        document.getElementById('detectedDialect').value = customDriverInfo.detectedDialect || customDriverInfo.detectedType || '';
        if (customDriverInfo.defaultPort) {
            document.getElementById('dbPort').value = customDriverInfo.defaultPort;
        }
        const knownDialect = customDriverInfo.detectedDialect && customDriverInfo.detectedDialect !== 'custom';
        statusEl.className = knownDialect ? 'driver-upload-status success' : 'driver-upload-status error';
        statusEl.textContent = `${customDriverInfo.message || '驱动已上传'}，驱动类：${customDriverInfo.driverClassName}`;
        showToast(knownDialect ? 'success' : 'warning', knownDialect ? '驱动识别成功' : '驱动已上传但未识别方言',
            customDriverInfo.displayName || customDriverInfo.driverClassName);
    } catch (e) {
        customDriverInfo = null;
        document.getElementById('customDriverId').value = '';
        document.getElementById('detectedDbType').value = '';
        document.getElementById('detectedDialect').value = '';
        statusEl.className = 'driver-upload-status error';
        statusEl.textContent = e.message;
        showToast('error', '驱动上传失败', e.message);
    } finally {
        event.target.value = '';
    }
}

async function testConnection() {
    const dbInfo = getDbInfo();
    const validationMessage = validateDbInfo(dbInfo);
    if (validationMessage) {
        showToast('error', '连接失败', validationMessage);
        return;
    }
    const body = document.getElementById('connectionTestBody');
    body.innerHTML = `
        <div class="result-card">
            <div class="result-icon info"><span class="loading-spinner"></span></div>
            <div class="result-title">正在测试连接</div>
            <div class="result-copy">正在校验数据库地址、认证信息与可达性。</div>
        </div>`;
    openModal('connectionTestModal');
    try {
        const res = await fetch('/api/database/connect', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(dbInfo)
        });
        const data = await res.json();
        if (data.success) {
            const db = getDbInfo();
            body.innerHTML = `
                <div class="result-card">
                    <div class="result-icon success">✓</div>
                    <div class="result-title">连接成功</div>
                    <div class="result-copy">数据库连接信息已通过校验，可以继续加载表列表。</div>
                    <div class="result-panel">
                        <div class="result-list">
                            <p><span>数据库类型</span><strong>${escapeHtml(db.type === 'dm' ? '达梦 DM' : db.type === 'mysql' ? 'MySQL' : '自定义')}</strong></p>
                            <p><span>服务器</span><strong>${escapeHtml(db.host)}:${escapeHtml(db.port)}</strong></p>
                            <p><span>数据库名</span><strong>${escapeHtml(db.databaseName || '-')}</strong></p>
                        </div>
                    </div>
                </div>`;
        } else {
            body.innerHTML = `
                <div class="result-card">
                    <div class="result-icon error">✗</div>
                    <div class="result-title">连接失败</div>
                    <div class="result-copy">当前配置未通过校验，请根据提示检查连接参数。</div>
                    <div class="result-panel">
                        <div class="result-list">
                            <p><span>错误信息</span><strong>${escapeHtml(data.message)}</strong></p>
                            <ul>
                                <li>主机地址和端口是否正确</li>
                                <li>数据库服务是否已启动</li>
                                <li>防火墙是否放行对应端口</li>
                            </ul>
                        </div>
                    </div>
                </div>`;
        }
    } catch (e) {
        body.innerHTML = `
            <div class="result-card">
                <div class="result-icon error">✗</div>
                <div class="result-title">连接失败</div>
                <div class="result-copy">网络请求未成功返回，请稍后重试。</div>
                <div class="result-panel">
                    <div class="result-list">
                        <p><span>网络错误</span><strong>${escapeHtml(e.message)}</strong></p>
                    </div>
                </div>
            </div>`;
    }
}

async function connectDatabase() {
    const dbInfo = getDbInfo();
    const validationMessage = validateDbInfo(dbInfo);
    if (validationMessage) {
        showToast('error', '连接失败', validationMessage);
        return;
    }
    try {
        const res = await fetch('/api/database/connect', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(dbInfo)
        });
        const data = await res.json();
        if (data.success) {
            connected = true;
            document.getElementById('connDot').classList.add('connected');
            const detected = data.data && (data.data.databaseProductName || data.data.detectedType);
            document.getElementById('connText').textContent = document.getElementById('dbName').value || detected || '已连接';
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
    if (!dbTables || dbTables.length === 0) {
        container.innerHTML = '<p class="placeholder-note">请先连接数据库</p>';
        updateSelectedCount();
        return;
    }

    const keyword = (filter || '').trim().toLowerCase();
    const matches = (table) => !keyword || table.tableName.toLowerCase().includes(keyword);
    const availableTables = dbTables.filter(t => !selectedTables.has(t.tableName) && matches(t));
    const selectedTableData = dbTables.filter(t => selectedTables.has(t.tableName));

    container.innerHTML = `
        <div class="table-picker-grid">
            <section class="table-picker-panel">
                <div class="table-picker-panel-header">
                    <span class="table-picker-panel-title">待选区</span>
                    <span class="table-picker-panel-meta">${availableTables.length} 个可选</span>
                </div>
                <div class="table-picker-list">
                    ${renderTablePickerRows(availableTables, 'add')}
                </div>
            </section>
            <section class="table-picker-panel">
                <div class="table-picker-panel-header">
                    <span class="table-picker-panel-title">已选区</span>
                    <span class="table-picker-panel-meta">${selectedTables.size} 个已选</span>
                </div>
                <div class="table-picker-list">
                    ${renderTablePickerRows(selectedTableData, 'remove')}
                </div>
            </section>
        </div>`;
    bindTablePickerRows(container);
    updateSelectedCount();
}

function renderTablePickerRows(tables, action) {
    if (tables.length === 0) {
        return `<div class="table-picker-empty">${action === 'add' ? '无匹配的待选表' : '暂无已选表'}</div>`;
    }
    return tables.map(t => {
        const rowCount = t.rowCount >= 0 ? t.rowCount.toLocaleString() : '-';
        const selected = action === 'remove';
        const actionText = selected ? '移除' : '添加';
        return `
            <div class="table-picker-row ${selected ? 'selected' : ''}" data-picker-action="${action}" data-table="${escapeAttr(t.tableName)}" title="${escapeAttr(t.tableName)}">
                <input type="checkbox" class="checkbox" ${selected ? 'checked' : ''} aria-label="${escapeAttr(actionText + t.tableName)}">
                <span class="table-picker-name">${escapeHtml(t.tableName)}</span>
                <span class="table-picker-count">${rowCount}</span>
                <button class="btn btn-xs btn-secondary" type="button">${actionText}</button>
            </div>`;
    }).join('');
}

function bindTablePickerRows(container) {
    container.querySelectorAll('.table-picker-row').forEach(row => {
        const runAction = () => handleTablePickerAction(row.dataset.pickerAction, row.dataset.table);
        row.addEventListener('click', runAction);
        row.querySelectorAll('input, button').forEach(control => {
            control.addEventListener('click', event => {
                event.stopPropagation();
                runAction();
            });
        });
    });
}

function handleTablePickerAction(action, tableName) {
    if (!tableName) return;
    if (action === 'add') selectedTables.add(tableName);
    if (action === 'remove') selectedTables.delete(tableName);
    renderTableList(document.getElementById('tableSearch').value);
}

function getVisibleTables() {
    const keyword = (document.getElementById('tableSearch').value || '').trim().toLowerCase();
    return dbTables.filter(t => !keyword || t.tableName.toLowerCase().includes(keyword));
}

function refreshTablePicker() {
    renderTableList(document.getElementById('tableSearch').value);
}

function toggleTable(name, checked) {
    if (checked) selectedTables.add(name); else selectedTables.delete(name);
    refreshTablePicker();
}
function selectAllTables() {
    getVisibleTables().forEach(t => selectedTables.add(t.tableName));
    refreshTablePicker();
}
function invertTables() {
    getVisibleTables().forEach(t => {
        if (selectedTables.has(t.tableName)) selectedTables.delete(t.tableName);
        else selectedTables.add(t.tableName);
    });
    refreshTablePicker();
}
function clearTables() {
    selectedTables.clear();
    refreshTablePicker();
}
function filterTables() {
    refreshTablePicker();
}
function updateSelectedCount() {
    const selectedCount = document.getElementById('selectedCount');
    if (selectedCount) selectedCount.textContent = '已选择: ' + selectedTables.size + ' 个表';
}

/* ====== Filter Toggles ====== */
function toggleTimeFilter() {
    document.getElementById('timeFilterFields').style.display = document.getElementById('enableTimeFilter').checked ? '' : 'none';
}
function toggleFieldFilter() {
    document.getElementById('fieldFilterFields').style.display = document.getElementById('enableFieldFilter').checked ? '' : 'none';
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
            resultDiv.innerHTML = data.data.details.map(d => {
                const status = d.valid ? 'success' : 'error';
                const icon = d.valid ? '✓' : '✗';
                return `<div class="validation-row ${status}"><span class="validation-icon">${icon}</span><span>第${d.line}行: ${escapeHtml(d.tableName)} - ${escapeHtml(d.message)}</span></div>`;
            }).join('');
        }
    } catch (e) { resultDiv.innerHTML = `<div class="validation-row error"><span class="validation-icon">✗</span><span>验证失败: ${escapeHtml(e.message)}</span></div>`; }
}

/* ====== Export ====== */
function buildExportConfig() {
    const format = document.querySelector('input[name="exportFormat"]:checked').value;
    const dbInfo = getDbInfo();
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
        maxConnections: parseInt(document.getElementById('maxConnections').value) || 5,
        dbType: dbInfo.type,
        dbHost: dbInfo.host,
        dbPort: dbInfo.port,
        dbName: dbInfo.databaseName,
        dbUser: dbInfo.username,
        dbPass: dbInfo.password,
        customDriverId: dbInfo.customDriverId,
        detectedType: dbInfo.detectedType,
        dialect: dbInfo.dialect,
        driverClassName: customDriverInfo ? customDriverInfo.driverClassName : '',
        driverDisplayName: customDriverInfo ? customDriverInfo.displayName : '',
        sqlFileMode: document.querySelector('input[name="sqlFileMode"]:checked').value
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
            document.getElementById('cancelBtn').style.display = '';
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
            const currentTableName = p.currentTable || '-';
            const currentTableNameEl = document.getElementById('currentTableName');
            currentTableNameEl.textContent = currentTableName;
            currentTableNameEl.title = currentTableName;
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
        <div class="result-card">
            <div class="result-icon success">✓</div>
            <div class="result-title">导出成功</div>
            <div class="result-copy">任务已完成，结果文件可以立即下载。</div>
            <div class="result-panel">
                <div class="result-list">
                    <p><span>导出表数</span><strong>${escapeHtml(p.totalTables)} 个</strong></p>
                    <p><span>成功表数</span><strong>${escapeHtml(p.successCount)} 个</strong></p>
                    <p><span>失败表数</span><strong>${escapeHtml(p.failCount)} 个</strong></p>
                    <p><span>总数据量</span><strong>${escapeHtml(p.totalRows.toLocaleString())} 条</strong></p>
                    <p><span>文件大小</span><strong>${(p.fileSize / 1024 / 1024).toFixed(1)} MB</strong></p>
                    <p><span>总耗时</span><strong>${formatTime(p.elapsedTime)}</strong></p>
                    <p class="result-note"><span>文件名</span><strong>${escapeHtml(p.fileName)}</strong></p>
                </div>
            </div>
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
            <td class="helper-text">${escapeHtml(t.description || '-')}</td>
            <td>${t.createTime ? new Date(t.createTime).toLocaleString() : '-'}</td>
            <td>
                <div class="table-inline-actions">
                    <button class="btn btn-sm btn-secondary" onclick="loadTemplateConfig(${t.id})">加载</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteTemplate(${t.id}, '${escapeAttr(t.name)}')">删除</button>
                </div>
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
        const btns = document.querySelectorAll('#page-export .mode-tab');
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
    if (config.dbType) { document.getElementById('dbType').value = config.dbType; onDbTypeChange(); }
    if (config.dbHost) document.getElementById('dbHost').value = config.dbHost;
    if (config.dbPort) document.getElementById('dbPort').value = config.dbPort;
    if (config.dbName) document.getElementById('dbName').value = config.dbName;
    if (config.dbUser) document.getElementById('dbUser').value = config.dbUser;
    if (config.dbPass) document.getElementById('dbPass').value = config.dbPass;
    if (config.customDriverId) {
        document.getElementById('customDriverId').value = config.customDriverId;
        document.getElementById('detectedDbType').value = config.detectedType || '';
        document.getElementById('detectedDialect').value = config.dialect || config.detectedType || '';
        document.getElementById('driverFileName').textContent = config.driverDisplayName || '已加载模板驱动引用';
        const statusEl = document.getElementById('driverUploadStatus');
        statusEl.className = 'driver-upload-status';
        statusEl.textContent = '模板包含历史驱动引用；如果连接失败，请重新上传 JDBC 驱动包。';
    }
    if (config.sqlFileMode) {
        const radio = document.querySelector(`input[name="sqlFileMode"][value="${config.sqlFileMode}"]`);
        if (radio) radio.checked = true;
    }
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
            <td class="result-note">${escapeHtml(f.fileName)}</td>
            <td>${(f.fileSize / 1024 / 1024).toFixed(1)} MB</td>
            <td>${f.createTime ? new Date(f.createTime).toLocaleString() : '-'}</td>
            <td>${renderStatusPill(f.status)}</td>
            <td>
                <div class="table-inline-actions records-inline-actions">
                    <button class="btn btn-sm btn-primary" onclick="downloadFile('${escapeAttr(f.fileName)}')">下载</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteFile('${escapeAttr(f.fileName)}')">删除</button>
                </div>
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

function confirmTableSelect() {
    closeModal('tableSelectModal');
}

/* ====== Modal Helpers ====== */
function syncModalState() {
    document.body.classList.toggle('modal-open', !!document.querySelector('.modal-overlay.active'));
}

function openModal(id) {
    document.getElementById(id).classList.add('active');
    syncModalState();
}

function closeModal(id) {
    document.getElementById(id).classList.remove('active');
    syncModalState();
}

function showConfirm(title, message, onConfirm) {
    document.getElementById('confirmTitle').textContent = title;
    document.getElementById('confirmBody').innerHTML = `
        <div class="result-card">
            <div class="result-icon warning">?</div>
            <div class="result-title">请确认操作</div>
            <div class="result-copy">${escapeHtml(message)}</div>
            <div class="result-panel">
                <div class="result-list">
                    <p><span>提示</span><strong>此操作不可撤销</strong></p>
                </div>
            </div>
        </div>`;
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
    toast.innerHTML = `<span class="toast-badge">${icons[type]||''}</span><div class="toast-content"><div class="toast-title">${escapeHtml(title)}</div>${message ? '<div class="toast-message">' + escapeHtml(message) + '</div>' : ''}</div>`;
    container.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; toast.style.transition = 'opacity 0.3s'; setTimeout(() => toast.remove(), 300); }, 3000);
}

/* ====== Utilities ====== */
function renderStatusPill(status) {
    const text = escapeHtml(status || '-');
    const normalized = String(status || '').toUpperCase();
    let level = 'success';
    if (normalized.includes('FAIL') || normalized.includes('ERROR') || normalized.includes('失败')) level = 'error';
    else if (normalized.includes('CANCEL') || normalized.includes('PENDING') || normalized.includes('WAIT') || normalized.includes('取消') || normalized.includes('等待')) level = 'warning';
    return `<span class="status-pill ${level}">${text}</span>`;
}

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
