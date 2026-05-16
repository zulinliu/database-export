(function () {
    'use strict';

    var STORAGE_KEY = 'theme';
    var EXPORT_POLL_INTERVAL = 1000;
    var state = {
        currentTab: 'export',
        isConnected: false,
        connectedDbInfo: null,
        tableList: [],
        selectedTables: [],
        currentTaskId: null,
        exportPollTimer: null,
        templatePage: 1,
        templatePageSize: 10,
        recordPage: 1,
        recordPageSize: 10,
        currentExportStatus: null
    };

    function $(id) { return document.getElementById(id); }

    function initTheme() {
        var stored = localStorage.getItem(STORAGE_KEY);
        var dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        var theme = (stored === 'dark' || (!stored && dark)) ? 'dark' : 'light';
        document.documentElement.setAttribute('data-theme', theme);
        $('sunIcon').style.display = theme === 'dark' ? '' : 'none';
        $('moonIcon').style.display = theme === 'dark' ? 'none' : '';
    }

    function toggleTheme() {
        var current = document.documentElement.getAttribute('data-theme');
        var next = current === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem(STORAGE_KEY, next);
        $('sunIcon').style.display = next === 'dark' ? '' : 'none';
        $('moonIcon').style.display = next === 'dark' ? 'none' : '';
    }

    function api(url, method, data, callback) {
        var xhr = new XMLHttpRequest();
        xhr.open(method || 'GET', url, true);
        xhr.setRequestHeader('Content-Type', 'application/json;charset=UTF-8');
        xhr.onload = function () {
            var resp = null;
            try { resp = JSON.parse(xhr.responseText); } catch (e) {}
            callback(resp, xhr.status, xhr);
        };
        xhr.onerror = function () { callback({ code: -1, message: '网络错误' }, 0, xhr); };
        if (data) xhr.send(JSON.stringify(data));
        else xhr.send();
    }

    function apiFile(url, method, data, filename, callback) {
        var xhr = new XMLHttpRequest();
        xhr.open(method || 'GET', url, true);
        xhr.responseType = 'blob';
        xhr.onload = function () {
            if (xhr.status === 200) {
                var blob = xhr.response;
                callback(blob, filename);
            } else {
                try {
                    var resp = JSON.parse(xhr.responseText);
                    callback(null, resp.message || '下载失败');
                } catch (e) {
                    callback(null, '下载失败');
                }
            }
        };
        xhr.onerror = function () { callback(null, '网络错误'); };
        if (data) xhr.send(JSON.stringify(data));
        else xhr.send();
    }

    function apiUpload(url, formData, callback) {
        var xhr = new XMLHttpRequest();
        xhr.open('POST', url, true);
        xhr.onload = function () {
            var resp = null;
            try { resp = JSON.parse(xhr.responseText); } catch (e) {}
            callback(resp, xhr.status);
        };
        xhr.onerror = function () { callback({ code: -1, message: '网络错误' }, 0); };
        xhr.send(formData);
    }

    function showToast(type, title, message) {
        var container = $('toastContainer');
        var icons = { success: '✓', error: '✗', warning: '⚠' };
        var toast = document.createElement('div');
        toast.className = 'toast toast-' + (type || 'success');
        toast.innerHTML = '<span class="toast-icon">' + (icons[type] || '✓') + '</span>' +
            '<div class="toast-content"><div class="toast-title">' + title + '</div>' +
            (message ? '<div class="toast-message">' + message + '</div>' : '') + '</div>';
        container.appendChild(toast);
        setTimeout(function () { toast.style.opacity = '0'; setTimeout(function () { container.removeChild(toast); }, 300); }, 4000);
    }

    function showModal(title, bodyHtml, buttons) {
        $('modalTitle').textContent = title;
        $('modalBody').innerHTML = bodyHtml;
        $('modalFooter').innerHTML = '';
        if (buttons) {
            buttons.forEach(function (btn) {
                var b = document.createElement('button');
                b.className = 'btn ' + (btn.className || 'btn-secondary');
                b.textContent = btn.text;
                b.onclick = function () {
                    if (btn.onClick) btn.onClick();
                    if (btn.closeOnClick !== false) hideModal();
                };
                $('modalFooter').appendChild(b);
            });
        }
        $('modalOverlay').classList.remove('hidden');
    }

    function hideModal() { $('modalOverlay').classList.add('hidden'); }

    function formatTime(ms) {
        if (!ms || ms < 0) return '--:--';
        var s = Math.floor(ms / 1000);
        var m = Math.floor(s / 60);
        s = s % 60;
        var h = Math.floor(m / 60);
        m = m % 60;
        if (h > 0) return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
        return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
    }

    function formatLogTime(timestamp) {
        var d = new Date(timestamp);
        return String(d.getHours()).padStart(2, '0') + ':' +
            String(d.getMinutes()).padStart(2, '0') + ':' +
            String(d.getSeconds()).padStart(2, '0');
    }

    function initTabs() {
        document.querySelectorAll('.nav-tab').forEach(function (tab) {
            tab.onclick = function () {
                var tabName = this.getAttribute('data-tab');
                document.querySelectorAll('.nav-tab').forEach(function (t) { t.classList.remove('active'); });
                this.classList.add('active');
                document.querySelectorAll('.tab-content').forEach(function (c) { c.classList.add('hidden'); });
                $('tab-' + tabName).classList.remove('hidden');
                state.currentTab = tabName;
                
                // 如果正在导出，确保轮询继续运行
                if (state.currentTaskId && (state.currentExportStatus && (state.currentExportStatus.status === 'RUNNING' || state.currentExportStatus.status === 'PENDING'))) {
                    if (!state.exportPollTimer) pollExportProgress();
                }
                
                if (tabName === 'templates') loadTemplateList();
                else if (tabName === 'records') loadRecordList();
            };
        });
    }

    function initExportTypeTabs() {
        document.querySelectorAll('.tabs .tab[data-export-type]').forEach(function (tab) {
            tab.onclick = function () {
                var type = this.getAttribute('data-export-type');
                document.querySelectorAll('.tabs .tab[data-export-type]').forEach(function (t) { t.classList.remove('active'); });
                this.classList.add('active');
                document.querySelectorAll('.export-type-content').forEach(function (c) { c.classList.add('hidden'); });
                $('exportType-' + type).classList.remove('hidden');
            };
        });
    }

    function initDbTypeChange() {
        $('dbType').onchange = function () {
            var type = this.value;
            if (type === 'dm') $('dbPort').value = '5236';
            else if (type === 'mysql') $('dbPort').value = '3306';
            else $('dbPort').value = '';
        };
    }

    function initFilterToggles() {
        $('enableTimeFilter').onchange = function () {
            $('timeFilterPanel').classList.toggle('hidden', !this.checked);
        };
        $('enableFieldFilter').onchange = function () {
            $('fieldFilterPanel').classList.toggle('hidden', !this.checked);
        };
        $('fieldFilterType').onchange = function () {
            var labels = { EQUAL: '参数值', IN: '参数值（逗号分隔）', RANGE: '范围值（如: 100-200）' };
            $('filterValueLabel').textContent = labels[this.value] || '参数值';
            $('fieldFilterValue').placeholder = this.value === 'IN' ? "'111','222','333'" : this.value === 'RANGE' ? '100-200' : '请输入参数值';
        };
    }

    function getDbInfo() {
        return {
            type: $('dbType').value,
            host: $('dbHost').value.trim(),
            port: parseInt($('dbPort').value, 10),
            databaseName: $('dbName').value.trim(),
            username: $('dbUser').value.trim(),
            password: $('dbPass').value
        };
    }

    function testConnection() {
        var info = getDbInfo();
        if (!info.host || !info.databaseName || !info.username) {
            showToast('error', '配置不完整', '请填写完整的数据库连接信息');
            return;
        }
        var btn = $('testConnBtn');
        btn.disabled = true;
        btn.textContent = '测试中...';
        api('/api/database/test', 'POST', info, function (resp) {
            btn.disabled = false;
            btn.textContent = '测试连接';
            if (resp && resp.code === 200) {
                showToast('success', '连接成功', resp.data ? '连接耗时: ' + resp.data + 'ms' : '');
            } else {
                showToast('error', '连接失败', (resp && resp.message) ? resp.message : '无法连接到数据库');
            }
        });
    }

    function connectDatabase() {
        var info = getDbInfo();
        if (!info.host || !info.databaseName || !info.username) {
            showToast('error', '配置不完整', '请填写完整的数据库连接信息');
            return;
        }
        var btn = $('connectBtn');
        btn.disabled = true;
        btn.textContent = '连接中...';
        api('/api/database/connect', 'POST', info, function (resp) {
            btn.disabled = false;
            btn.textContent = '连接数据库';
            if (resp && resp.code === 200) {
                state.isConnected = true;
                state.connectedDbInfo = info;
                updateConnectionStatus(true, info.type + ' - ' + info.databaseName);
                showToast('success', '连接成功', '已连接到 ' + info.databaseName);
                loadTableList();
            } else {
                showToast('error', '连接失败', (resp && resp.message) ? resp.message : '连接数据库失败');
            }
        });
    }

    function disconnectDatabase() {
        api('/api/database/disconnect', 'POST', null, function (resp) {
            state.isConnected = false;
            state.connectedDbInfo = null;
            state.tableList = [];
            state.selectedTables = [];
            updateConnectionStatus(false);
            showToast('success', '已断开', '数据库连接已断开');
        });
    }

    function updateConnectionStatus(connected, text) {
        var statusEl = $('connectionStatus');
        var dot = statusEl.querySelector('.status-dot');
        var textEl = statusEl.querySelector('.status-text');
        if (connected) {
            dot.classList.remove('disconnected');
            dot.classList.add('connected');
            textEl.textContent = text || '已连接';
        } else {
            dot.classList.remove('connected');
            dot.classList.add('disconnected');
            textEl.textContent = '未连接';
        }
    }

    function loadTableList() {
        api('/api/database/tables?page=1&pageSize=10000', 'GET', null, function (resp) {
            if (resp && resp.code === 200) {
                state.tableList = resp.data || [];
                renderTableList();
            }
        });
    }

    function renderTableList() {
        var container = $('tableListContainer');
        var tables = state.tableList;
        if (!tables || tables.length === 0) {
            container.innerHTML = '<div class="empty-state"><span class="empty-state-icon">📋</span><div class="empty-state-title">暂无表</div></div>';
            return;
        }
        var html = '<table class="data-table"><thead><tr><th class="checkbox-cell"><input type="checkbox" id="tableSelectAll"></th><th>表名</th><th>记录数</th></tr></thead><tbody>';
        tables.forEach(function (t, i) {
            var checked = state.selectedTables.indexOf(t.tableName) >= 0 ? 'checked' : '';
            html += '<tr><td class="checkbox-cell"><input type="checkbox" data-table="' + t.tableName + '" ' + checked + '></td><td>' + t.tableName + '</td><td>' + (t.rowCount || '-') + '</td></tr>';
        });
        html += '</tbody></table>';
        container.innerHTML = html;
        container.querySelectorAll('input[data-table]').forEach(function (cb) {
            cb.onchange = function () {
                var name = this.getAttribute('data-table');
                if (this.checked) {
                    if (state.selectedTables.indexOf(name) < 0) state.selectedTables.push(name);
                } else {
                    state.selectedTables = state.selectedTables.filter(function (n) { return n !== name; });
                }
                updateSelectedTableCount();
            };
        });
        $('tableSelectAll').onchange = function () {
            var checked = this.checked;
            container.querySelectorAll('input[data-table]').forEach(function (cb) { cb.checked = checked; });
            state.selectedTables = checked ? tables.map(function (t) { return t.tableName; }) : [];
            updateSelectedTableCount();
        };
        updateSelectedTableCount();
    }

    function updateSelectedTableCount() {
        $('selectedTableCount').textContent = '已选择: ' + state.selectedTables.length + ' 个表';
    }

    function initTableSearch() {
        $('tableSearchInput').oninput = function () {
            var keyword = this.value.trim().toLowerCase();
            var container = $('tableListContainer');
            if (!state.tableList.length) return;
            if (!keyword) {
                renderTableList();
                return;
            }
            var filtered = state.tableList.filter(function (t) { return t.tableName.toLowerCase().indexOf(keyword) >= 0; });
            var html = '<table class="data-table"><thead><tr><th class="checkbox-cell"><input type="checkbox" id="tableSelectAllFiltered"></th><th>表名</th><th>记录数</th></tr></thead><tbody>';
            filtered.forEach(function (t) {
                var checked = state.selectedTables.indexOf(t.tableName) >= 0 ? 'checked' : '';
                html += '<tr><td class="checkbox-cell"><input type="checkbox" data-table="' + t.tableName + '" ' + checked + '></td><td>' + t.tableName + '</td><td>' + (t.rowCount || '-') + '</td></tr>';
            });
            html += '</tbody></table>';
            container.innerHTML = html;
            container.querySelectorAll('input[data-table]').forEach(function (cb) {
                cb.onchange = function () {
                    var name = this.getAttribute('data-table');
                    if (this.checked) { if (state.selectedTables.indexOf(name) < 0) state.selectedTables.push(name); }
                    else { state.selectedTables = state.selectedTables.filter(function (n) { return n !== name; }); }
                    updateSelectedTableCount();
                };
            });
            $('tableSelectAllFiltered').onchange = function () {
                var checked = this.checked;
                container.querySelectorAll('input[data-table]').forEach(function (cb) { cb.checked = checked; });
                state.selectedTables = checked ? filtered.map(function (t) { return t.tableName; }) : [];
                updateSelectedTableCount();
            };
        };

        $('selectAllTablesBtn').onclick = function () {
            state.selectedTables = state.tableList.map(function (t) { return t.tableName; });
            renderTableList();
        };
        $('deselectAllTablesBtn').onclick = function () {
            state.selectedTables = [];
            renderTableList();
        };
    }

    function getExportConfig() {
        var exportType = document.querySelector('.tabs .tab[data-export-type].active').getAttribute('data-export-type');
        var tables = '';
        if (exportType === 'customTables') {
            tables = $('customTablesInput').value.trim();
        } else if (exportType === 'tableSelect') {
            tables = state.selectedTables.join(',');
        } else if (exportType === 'customSql') {
            // tables not used for customSql
        }

        var formats = [];
        if ($('exportExcel').checked) formats.push('excel');
        if ($('exportSql').checked) formats.push('sql');

        var config = {
            exportType: exportType,
            tables: tables,
            customSql: exportType === 'customSql' ? $('customSqlInput').value.trim() : null,
            enableTimeFilter: $('enableTimeFilter').checked,
            timeFieldNames: $('timeFieldNames').value.trim(),
            startDate: $('startDate').value,
            endDate: $('endDate').value,
            enableFieldFilter: $('enableFieldFilter').checked,
            fieldFilter: $('enableFieldFilter').checked ? {
                fieldName: $('fieldFilterName').value.trim(),
                filterType: $('fieldFilterType').value,
                filterValue: $('fieldFilterValue').value.trim(),
                enabled: true
            } : null,
            exportFormats: formats.join(','),
            maxConnections: parseInt($('maxConnections').value, 10)
        };
        return config;
    }

    function validateExportConfig(config) {
        if (config.exportType === 'customTables' && !config.tables) return '请输入表名';
        if (config.exportType === 'tableSelect' && (!state.selectedTables || state.selectedTables.length === 0)) return '请选择要导出的表';
        if (config.exportType === 'customSql' && !config.customSql) return '请输入SQL语句';
        if (!state.isConnected) return '请先连接数据库';
        if (!config.exportFormats) return '请至少选择一种导出格式';
        if (config.enableTimeFilter && (!config.startDate || !config.endDate)) return '请填写完整的时间范围';
        if (config.enableTimeFilter && config.startDate > config.endDate) return '开始日期不能大于结束日期';
        if (config.enableFieldFilter && config.fieldFilter) {
            if (!config.fieldFilter.fieldName) return '请填写字段过滤的字段名';
            if (!config.fieldFilter.filterValue) return '请填写字段过滤的参数值';
        }
        return null;
    }

    function startExport() {
        var config = getExportConfig();
        var err = validateExportConfig(config);
        if (err) { showToast('error', '配置错误', err); return; }

        $('exportProgressPanel').classList.remove('hidden');
        $('startExportBtn').disabled = true;
        $('startExportBtn').textContent = '导出中...';

        var logViewer = $('logViewer');
        logViewer.innerHTML = '<div class="log-entry"><span class="log-time">[' + formatLogTime(Date.now()) + ']</span><span class="log-info">开始导出任务...</span></div>';

        api('/api/export/start', 'POST', config, function (resp) {
            if (resp && resp.code === 200) {
                state.currentTaskId = resp.data;
                addLog('INFO', '任务已创建: ' + state.currentTaskId);
                addLog('INFO', '正在导出 ' + (config.exportType === 'customTables' ? config.tables.split(/[，,;\n]/).filter(function(t){return t.trim();}).length : (config.exportType === 'tableSelect' ? state.selectedTables.length : '自定义SQL')) + ' 个对象...');
                pollExportProgress();
            } else {
                showToast('error', '启动失败', (resp && resp.message) || '无法启动导出任务');
                $('exportProgressPanel').classList.add('hidden');
                $('startExportBtn').disabled = false;
                $('startExportBtn').textContent = '🚀 开始导出';
            }
        });
    }

    function pollExportProgress() {
        if (!state.currentTaskId) return;
        api('/api/export/progress/' + state.currentTaskId, 'GET', null, function (resp) {
            if (!resp || !resp.data) return;
            var p = resp.data;
            state.currentExportStatus = p;
            updateProgressUI(p);
            if (p.status === 'RUNNING' || p.status === 'PENDING') {
                state.exportPollTimer = setTimeout(pollExportProgress, EXPORT_POLL_INTERVAL);
            } else {
                finishExport(p);
            }
        });
    }

    function updateProgressUI(p) {
        var progress = p.totalRows > 0 ? Math.round((p.exportedRows || 0) / p.totalRows * 100) : 0;
        progress = Math.min(progress, 100);
        $('progressFill').style.width = progress + '%';
        $('progressText').textContent = progress + '%';
        $('currentTableInfo').textContent = '当前表: ' + (p.currentTable || '-') + (p.currentTableRows ? ' (' + p.currentTableRows + ' 条)' : '');
        $('overallProgress').textContent = '已完成: ' + (p.completedTables || 0) + '/' + (p.totalTables || 0) + ' 个表';
        $('totalRowsInfo').textContent = '总数据: ' + (p.exportedRows || 0) + ' 条';
        var elapsed = p.startTime ? Date.now() - p.startTime : 0;
        $('elapsedTime').textContent = '已用时: ' + formatTime(elapsed);
        if (p.estimatedRemaining && p.estimatedRemaining > 0) {
            $('estimatedTime').textContent = '预计剩余: ' + formatTime(p.estimatedRemaining);
        }
        if (p.logs && p.logs.length) {
            var logViewer = $('logViewer');
            var lastLog = p.logs[p.logs.length - 1];
            if (lastLog) {
                var levelClass = { INFO: 'log-info', SUCCESS: 'log-success', WARNING: 'log-warning', ERROR: 'log-error' };
                logViewer.innerHTML += '<div class="log-entry"><span class="log-time">[' + formatLogTime(lastLog.timestamp) + ']</span><span class="' + (levelClass[lastLog.level] || 'log-info') + '">' + lastLog.message + '</span></div>';
                logViewer.scrollTop = logViewer.scrollHeight;
            }
        }
    }

    function addLog(level, message) {
        var logViewer = $('logViewer');
        var levelClass = { INFO: 'log-info', SUCCESS: 'log-success', WARNING: 'log-warning', ERROR: 'log-error' };
        logViewer.innerHTML += '<div class="log-entry"><span class="log-time">[' + formatLogTime(Date.now()) + ']</span><span class="' + (levelClass[level] || 'log-info') + '">' + message + '</span></div>';
        logViewer.scrollTop = logViewer.scrollHeight;
    }

    function finishExport(p) {
        clearTimeout(state.exportPollTimer);
        state.currentTaskId = null;
        $('startExportBtn').disabled = false;
        $('startExportBtn').textContent = '🚀 开始导出';

        if (p.status === 'COMPLETED') {
            showModal('导出完成', '<div style="text-align:center"><div style="font-size:48px;margin-bottom:16px">✓</div><div style="font-size:18px;font-weight:600;margin-bottom:8px">导出成功！</div><div style="color:var(--text-secondary)">文件: ' + (p.exportFileName || '-') + '<br>大小: ' + (p.fileSize ? formatFileSize(p.fileSize) : '-') + '<br>总数据: ' + (p.exportedRows || 0) + ' 条</div></div>', [
                { text: '确定', className: 'btn-primary' }
            ]);
            showToast('success', '导出成功', p.exportFileName || '');
        } else if (p.status === 'FAILED') {
            showModal('导出失败', '<div style="text-align:center"><div style="font-size:48px;margin-bottom:16px;color:var(--error)">✗</div><div style="font-size:18px;font-weight:600;margin-bottom:8px">导出失败</div><div style="color:var(--text-secondary)">' + ((p.errors && p.errors.length) ? p.errors.join('<br>') : '未知错误') + '</div></div>', [
                { text: '确定', className: 'btn-secondary' }
            ]);
            showToast('error', '导出失败', (p.errors && p.errors[0]) || '');
        } else if (p.status === 'CANCELLED') {
            showToast('warning', '已取消', '导出任务已被取消');
        }
    }

    function formatFileSize(bytes) {
        if (!bytes) return '-';
        var units = ['B', 'KB', 'MB', 'GB'];
        var i = 0;
        var size = bytes;
        while (size >= 1024 && i < units.length - 1) { size /= 1024; i++; }
        return size.toFixed(1) + ' ' + units[i];
    }

    function cancelExport() {
        if (!state.currentTaskId) return;
        showModal('确认取消', '<div style="text-align:center"><div style="font-size:48px;margin-bottom:16px">?</div><div style="font-size:16px">确定要取消当前导出任务吗？</div></div>', [
            { text: '取消', className: 'btn-secondary' },
            {
                text: '确定取消', className: 'btn-danger',
                onClick: function () {
                    api('/api/export/cancel/' + state.currentTaskId, 'POST', null, function (resp) {
                        clearTimeout(state.exportPollTimer);
                        state.currentTaskId = null;
                        addLog('WARNING', '用户取消了导出任务');
                        $('startExportBtn').disabled = false;
                        $('startExportBtn').textContent = '🚀 开始导出';
                    });
                }
            }
        ]);
    }

    function validateSql() {
        var sql = $('customSqlInput').value.trim();
        if (!sql) { showToast('error', 'SQL为空', '请输入SQL语句'); return; }
        var lines = sql.split('\n').filter(function (l) { return l.trim(); });
        api('/api/sql/validate', 'POST', { sqls: lines }, function (resp) {
            if (resp && resp.code === 200) {
                var results = resp.data || [];
                var html = '<div style="font-family:var(--font-mono);font-size:13px">';
                var hasError = false;
                results.forEach(function (r, i) {
                    var ok = r.valid;
                    if (!ok) hasError = true;
                    html += '<div style="padding:4px 0;color:' + (ok ? 'var(--success)' : 'var(--error)') + '">' +
                        (ok ? '✓' : '✗') + ' 第' + (i + 1) + '行: ' + r.message + '</div>';
                });
                html += '</div>';
                $('sqlValidationResult').innerHTML = html;
                if (!hasError) showToast('success', '验证通过', '所有SQL语句验证通过');
                else showToast('error', '验证失败', '部分SQL语句验证未通过');
            } else {
                showToast('error', '验证失败', (resp && resp.message) || '验证过程出错');
            }
        });
    }

    function initSaveTemplate() {
        $('saveTemplateBtn').onclick = function () {
            var config = getExportConfig();
            showModal('保存模板', '<div class="form-group"><label class="form-label">模板名称 *</label><input type="text" id="templateNameInput" class="form-input" placeholder="输入模板名称"></div><div class="form-group"><label class="form-label">描述</label><textarea id="templateDescInput" class="form-input" placeholder="模板描述（可选）" rows="3"></textarea></div>', [
                { text: '取消', className: 'btn-secondary' },
                {
                    text: '保存', className: 'btn-primary',
                    onClick: function () {
                        var name = $('templateNameInput').value.trim();
                        if (!name) { showToast('error', '请填写', '模板名称不能为空'); return; }
                        var template = {
                            name: name,
                            description: $('templateDescInput').value.trim(),
                            configJson: JSON.stringify(config)
                        };
                        api('/api/template/save', 'POST', template, function (resp) {
                            if (resp && resp.code === 200) {
                                showToast('success', '保存成功', '模板"' + name + '"已保存');
                            } else {
                                showToast('error', '保存失败', (resp && resp.message) || '保存模板失败');
                            }
                        });
                    }
                }
            ]);
        };
    }

    function loadTemplateList() {
        api('/api/template/list', 'GET', null, function (resp) {
            if (resp && resp.code === 200) {
                renderTemplateList(resp.data || []);
            }
        });
    }

    function renderTemplateList(templates) {
        var container = $('templateListContainer');
        if (!templates || templates.length === 0) {
            container.innerHTML = '<div class="empty-state"><span class="empty-state-icon">📋</span><div class="empty-state-title">暂无模板</div><div class="empty-state-desc">您还没有保存任何模板</div><button class="btn btn-secondary" onclick="document.querySelector(\'[data-tab=export]\').click()">去导出数据</button></div>';
            return;
        }
        var html = '<table class="data-table"><thead><tr><th class="checkbox-cell"><input type="checkbox" id="templateSelectAll"></th><th>模板名称</th><th>描述</th><th>创建时间</th><th>操作</th></tr></thead><tbody>';
        templates.forEach(function (t) {
            var createTime = t.createTime ? new Date(t.createTime).toLocaleString('zh-CN') : '-';
            html += '<tr>' +
                '<td class="checkbox-cell"><input type="checkbox" class="template-check" data-id="' + t.id + '"></td>' +
                '<td><strong>' + (t.name || '') + '</strong></td>' +
                '<td>' + (t.description || '-') + '</td>' +
                '<td>' + createTime + '</td>' +
                '<td><button class="btn btn-secondary btn-sm" onclick="window.loadTemplate(' + t.id + ')">加载</button> <button class="btn btn-danger btn-sm" onclick="window.deleteTemplate(' + t.id + ')">删除</button></td>' +
                '</tr>';
        });
        html += '</tbody></table>';
        container.innerHTML = html;
        $('templateSelectAll').onchange = function () {
            container.querySelectorAll('.template-check').forEach(function (cb) { cb.checked = this.checked; }.bind(this));
        };
    }

    window.loadTemplate = function (id) {
        api('/api/template/' + id, 'GET', null, function (resp) {
            if (resp && resp.code === 200 && resp.data) {
                var t = resp.data;
                var config = null;
                try { config = JSON.parse(t.configJson); } catch (e) { config = t.toExportConfig ? t.toExportConfig() : null; }
                if (!config) { showToast('error', '加载失败', '模板配置解析失败'); return; }
                applyConfigToUI(config);
                showToast('success', '加载成功', '模板"' + t.name + '"已加载');
                document.querySelector('[data-tab=export]').click();
            } else {
                showToast('error', '加载失败', (resp && resp.message) || '加载模板失败');
            }
        });
    };

    window.deleteTemplate = function (id) {
        showModal('确认删除', '<div style="text-align:center"><div style="font-size:48px;margin-bottom:16px">?</div><div style="font-size:16px">确定要删除这个模板吗？<br>此操作不可撤销。</div></div>', [
            { text: '取消', className: 'btn-secondary' },
            {
                text: '确定删除', className: 'btn-danger',
                onClick: function () {
                    api('/api/template/delete', 'POST', { id: id }, function (resp) {
                        if (resp && resp.code === 200) {
                            showToast('success', '已删除', '模板已删除');
                            loadTemplateList();
                        } else {
                            showToast('error', '删除失败', (resp && resp.message) || '删除模板失败');
                        }
                    });
                }
            }
        ]);
    };

    function applyConfigToUI(config) {
        if (!config) return;
        if (config.exportType) {
            document.querySelectorAll('.tabs .tab[data-export-type]').forEach(function (tab) {
                if (tab.getAttribute('data-export-type') === config.exportType) tab.click();
            });
        }
        if (config.tables) $('customTablesInput').value = config.tables;
        if (config.customSql) $('customSqlInput').value = config.customSql;
        if (config.enableTimeFilter !== undefined) $('enableTimeFilter').checked = config.enableTimeFilter;
        $('timeFilterPanel').classList.toggle('hidden', !config.enableTimeFilter);
        if (config.timeFieldNames) $('timeFieldNames').value = config.timeFieldNames;
        if (config.startDate) $('startDate').value = config.startDate;
        if (config.endDate) $('endDate').value = config.endDate;
        if (config.enableFieldFilter !== undefined) $('enableFieldFilter').checked = config.enableFieldFilter;
        $('fieldFilterPanel').classList.toggle('hidden', !config.enableFieldFilter);
        if (config.fieldFilter) {
            $('fieldFilterName').value = config.fieldFilter.fieldName || '';
            $('fieldFilterType').value = config.fieldFilter.filterType || 'EQUAL';
            $('fieldFilterValue').value = config.fieldFilter.filterValue || '';
        }
        var formats = (config.exportFormats || '').split(',');
        $('exportExcel').checked = formats.indexOf('excel') >= 0;
        $('exportSql').checked = formats.indexOf('sql') >= 0;
        if (config.maxConnections) $('maxConnections').value = config.maxConnections;
    }

    $('exportSelectedTemplatesBtn').onclick = function () {
        var checks = document.querySelectorAll('.template-check:checked');
        var ids = Array.prototype.map.call(checks, function (cb) { return parseInt(cb.getAttribute('data-id'), 10); });
        if (ids.length === 0) { showToast('warning', '请选择', '请先选择要导出的模板'); return; }
        apiFile('/api/template/export', 'POST', { ids: ids }, 'templates.zip', function (blob, filename) {
            if (blob) {
                var url = URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = url; a.download = filename; a.click();
                URL.revokeObjectURL(url);
                showToast('success', '导出成功', '已导出 ' + ids.length + ' 个模板');
            } else {
                showToast('error', '导出失败', filename);
            }
        });
    };

    $('importTemplateBtn').onclick = function () { $('importFileInput').click(); };
    $('importFileInput').onchange = function () {
        var file = this.files[0];
        if (!file) return;
        var formData = new FormData();
        formData.append('file', file);
        apiUpload('/api/template/import', formData, function (resp) {
            if (resp && resp.code === 200) {
                showToast('success', '导入成功', '已导入 ' + ((resp.data && resp.data.length) || 0) + ' 个模板');
                loadTemplateList();
            } else {
                showToast('error', '导入失败', (resp && resp.message) || '导入模板失败');
            }
            $('importFileInput').value = '';
        });
    };

    function loadRecordList() {
        api('/api/export/files', 'GET', null, function (resp) {
            if (resp && resp.code === 200) {
                renderRecordList(resp.data || []);
            }
        });
    }

    function renderRecordList(records) {
        var container = $('recordListContainer');
        if (!records || records.length === 0) {
            container.innerHTML = '<div class="empty-state"><span class="empty-state-icon">📦</span><div class="empty-state-title">暂无导出记录</div><div class="empty-state-desc">您还没有导出过数据</div><button class="btn btn-secondary" onclick="document.querySelector(\'[data-tab=export]\').click()">开始导出</button></div>';
            return;
        }
        var html = '<table class="data-table"><thead><tr><th class="checkbox-cell"><input type="checkbox" id="recordSelectAll"></th><th>文件名</th><th>大小</th><th>导出时间</th><th>操作</th></tr></thead><tbody>';
        records.forEach(function (r) {
            var createTime = r.createTime ? new Date(r.createTime).toLocaleString('zh-CN') : '-';
            html += '<tr>' +
                '<td class="checkbox-cell"><input type="checkbox" class="record-check" data-file="' + r.fileName + '"></td>' +
                '<td><span style="font-family:var(--font-mono);font-size:12px">' + (r.fileName || '') + '</span></td>' +
                '<td>' + (r.fileSize ? formatFileSize(r.fileSize) : '-') + '</td>' +
                '<td>' + createTime + '</td>' +
                '<td><button class="btn btn-primary btn-sm" onclick="window.downloadRecord(\'' + (r.fileName || '') + '\')">下载</button> <button class="btn btn-danger btn-sm" onclick="window.deleteRecord([\'' + (r.fileName || '') + '\'])">删除</button></td>' +
                '</tr>';
        });
        html += '</tbody></table>';
        container.innerHTML = html;
        $('recordSelectAll').onchange = function () {
            container.querySelectorAll('.record-check').forEach(function (cb) { cb.checked = this.checked; }.bind(this));
        };
    }

    window.downloadRecord = function (filename) {
        if (!filename) return;
        window.open('/api/export/download/' + encodeURIComponent(filename), '_blank');
    };

    window.deleteRecord = function (filenames) {
        if (!filenames || filenames.length === 0) {
            var checks = document.querySelectorAll('.record-check:checked');
            filenames = Array.prototype.map.call(checks, function (cb) { return cb.getAttribute('data-file'); });
        }
        if (!filenames.length) { showToast('warning', '请选择', '请先选择要删除的记录'); return; }
        showModal('确认删除', '<div style="text-align:center"><div style="font-size:16px">确定要删除选中的 ' + filenames.length + ' 个文件吗？<br>此操作不可撤销。</div></div>', [
            { text: '取消', className: 'btn-secondary' },
            {
                text: '确定删除', className: 'btn-danger',
                onClick: function () {
                    api('/api/export/delete', 'POST', { fileNames: filenames }, function (resp) {
                        if (resp && resp.code === 200) {
                            showToast('success', '已删除', '已删除 ' + filenames.length + ' 个文件');
                            loadRecordList();
                        } else {
                            showToast('error', '删除失败', (resp && resp.message) || '删除失败');
                        }
                    });
                }
            }
        ]);
    };

    $('deleteSelectedRecordsBtn').onclick = function () { window.deleteRecord([]); };

    $('cleanOldRecordsBtn').onclick = function () {
        showModal('清空过期记录', '<div class="form-group"><label class="form-label">删除多少天前的记录？</label><select id="cleanDays" class="form-select"><option value="7">7天前</option><option value="14">14天前</option><option value="30" selected>30天前</option><option value="60">60天前</option><option value="90">90天前</option></select></div>', [
            { text: '取消', className: 'btn-secondary' },
            {
                text: '确定清空', className: 'btn-danger',
                onClick: function () {
                    var days = parseInt($('cleanDays').value, 10);
                    api('/api/export/clean?days=' + days, 'POST', null, function (resp) {
                        if (resp && resp.code === 200) {
                            showToast('success', '清空成功', '已清空 ' + (resp.data || 0) + ' 个过期文件');
                            loadRecordList();
                        } else {
                            showToast('error', '清空失败', (resp && resp.message) || '清空失败');
                        }
                    });
                }
            }
        ]);
    };

    function initLogout() {
        $('logoutBtn').onclick = function () {
            api('/api/logout', 'POST', null, function () {
                window.location.href = '/login';
            });
        };
    }

    function initCheckLogin() {
        api('/api/checkLogin', 'GET', null, function (resp) {
            if (!resp || resp.code !== 200) {
                window.location.href = '/login';
            }
        });
    }

    function initAll() {
        initTheme();
        initCheckLogin();
        initTabs();
        initExportTypeTabs();
        initDbTypeChange();
        initFilterToggles();
        initTableSearch();
        initSaveTemplate();
        initLogout();

        $('themeToggle').onclick = toggleTheme;
        $('testConnBtn').onclick = testConnection;
        $('connectBtn').onclick = connectDatabase;
        $('startExportBtn').onclick = startExport;
        $('cancelExportBtn').onclick = cancelExport;
        $('validateSqlBtn').onclick = validateSql;
        $('modalClose').onclick = hideModal;
        $('modalOverlay').onclick = function (e) { if (e.target === this) hideModal(); };
    }

    document.addEventListener('DOMContentLoaded', initAll);
})();