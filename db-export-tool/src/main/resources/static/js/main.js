const api = {
    async request(url, options = {}) {
        const defaultOptions = {
            headers: { 'Content-Type': 'application/json' },
            ...options
        };
        const response = await fetch(url, defaultOptions);
        const data = await response.json();
        if (response.status === 401) {
            window.location.href = '/login.html';
            throw new Error('Unauthorized');
        }
        return data;
    },
    get(url) { return this.request(url); },
    post(url, body) {
        return this.request(url, {
            method: 'POST',
            body: JSON.stringify(body)
        });
    },
    delete(url) {
        return this.request(url, { method: 'DELETE' });
    }
};

const util = {
    formatDate(timestamp) {
        if (!timestamp) return '';
        const date = new Date(timestamp);
        return date.toLocaleString('zh-CN');
    },
    formatFileSize(bytes) {
        if (!bytes) return '0 B';
        const units = ['B', 'KB', 'MB', 'GB'];
        let size = bytes;
        let unitIndex = 0;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return `${size.toFixed(2)} ${units[unitIndex]}`;
    },
    formatDuration(ms) {
        if (!ms) return '0s';
        const seconds = Math.floor(ms / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        if (hours > 0) {
            return `${hours}h ${minutes % 60}m ${seconds % 60}s`;
        } else if (minutes > 0) {
            return `${minutes}m ${seconds % 60}s`;
        }
        return `${seconds}s`;
    }
};

const toast = {
    show(message, type = 'success') {
        const div = document.createElement('div');
        div.className = `toast toast-${type}`;
        div.textContent = message;
        document.body.appendChild(div);
        setTimeout(() => div.remove(), 3000);
    },
    success(message) { this.show(message, 'success'); },
    error(message) { this.show(message, 'error'); },
    warning(message) { this.show(message, 'warning'); }
};

const modal = {
    current: null,
    show(html) {
        this.hide();
        const div = document.createElement('div');
        div.className = 'modal-overlay';
        div.innerHTML = `
            <div class="modal">
                ${html}
            </div>
        `;
        div.addEventListener('click', (e) => {
            if (e.target === div) this.hide();
        });
        document.body.appendChild(div);
        this.current = div;
    },
    hide() {
        if (this.current) {
            this.current.remove();
            this.current = null;
        }
    }
};

const app = {
    currentPage: 'export',
    dbInfo: {},
    exportConfig: {
        exportType: 'customTables',
        tables: '',
        customSql: '',
        enableTimeFilter: false,
        timeFieldNames: 'create_time,update_time',
        startDate: '',
        endDate: '',
        enableFieldFilter: false,
        fieldFilter: {
            fieldName: '',
            filterType: 'equal',
            filterValue: '',
            enabled: false
        },
        exportFormats: 'excel,sql',
        maxConnections: 5
    },
    currentTaskId: null,
    progressInterval: null,
    templates: [],
    exportFiles: [],
    allTables: [],
    selectedTables: [],
    
    async init() {
        this.checkLogin();
        this.bindEvents();
        this.loadTemplates();
        this.loadExportFiles();
        this.setTodayDate();
    },
    
    async checkLogin() {
        try {
            const result = await api.get('/api/check-login');
            if (result.success && result.data) {
                document.getElementById('userName').textContent = result.data.username;
            } else {
                window.location.href = '/login.html';
            }
        } catch (e) {
            window.location.href = '/login.html';
        }
    },
    
    setTodayDate() {
        const today = new Date().toISOString().split('T')[0];
        document.getElementById('startDate').value = today;
        document.getElementById('endDate').value = today;
    },
    
    bindEvents() {
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', () => {
                this.switchPage(item.dataset.page);
            });
        });
        
        document.querySelectorAll('.export-type-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.export-type-tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                this.exportConfig.exportType = tab.dataset.type;
                document.querySelectorAll('.export-type-content').forEach(c => c.style.display = 'none');
                document.getElementById(`${tab.dataset.type}Content`).style.display = 'block';
            });
        });
        
        document.getElementById('testConnectionBtn').addEventListener('click', () => this.testConnection());
        document.getElementById('connectDatabaseBtn').addEventListener('click', () => this.connectDatabase());
        document.getElementById('selectTablesBtn').addEventListener('click', () => this.showTableSelector());
        document.getElementById('startExportBtn').addEventListener('click', () => this.startExport());
        document.getElementById('saveTemplateBtn').addEventListener('click', () => this.showSaveTemplateModal());
        document.getElementById('loadTemplateBtn').addEventListener('click', () => this.showLoadTemplateModal());
        
        document.getElementById('themeToggle').addEventListener('click', () => this.toggleTheme());
        document.getElementById('logoutBtn').addEventListener('click', () => this.logout());
        
        this.bindCheckbox('enableTimeFilter', 'timeFilterSection');
        this.bindCheckbox('enableFieldFilter', 'fieldFilterSection');
    },
    
    bindCheckbox(checkboxId, sectionId) {
        const checkbox = document.getElementById(checkboxId);
        const section = document.getElementById(sectionId);
        checkbox.addEventListener('change', () => {
            section.style.display = checkbox.checked ? 'block' : 'none';
            if (checkboxId === 'enableTimeFilter') {
                this.exportConfig.enableTimeFilter = checkbox.checked;
            } else if (checkboxId === 'enableFieldFilter') {
                this.exportConfig.enableFieldFilter = checkbox.checked;
                this.exportConfig.fieldFilter.enabled = checkbox.checked;
            }
        });
    },
    
    toggleTheme() {
        const html = document.documentElement;
        const isDark = html.getAttribute('data-theme') === 'dark';
        html.setAttribute('data-theme', isDark ? 'light' : 'dark');
    },
    
    async logout() {
        await api.post('/api/logout');
        window.location.href = '/login.html';
    },
    
    switchPage(page) {
        this.currentPage = page;
        document.querySelectorAll('.nav-item').forEach(item => {
            item.classList.toggle('active', item.dataset.page === page);
        });
        document.querySelectorAll('.page-content').forEach(c => c.style.display = 'none');
        document.getElementById(`${page}Page`).style.display = 'block';
        
        if (page === 'history') {
            this.loadExportFiles();
        } else if (page === 'templates') {
            this.renderTemplates();
        }
    },
    
    getDbInfo() {
        return {
            dbType: document.getElementById('dbType').value,
            host: document.getElementById('dbHost').value,
            port: parseInt(document.getElementById('dbPort').value),
            databaseName: document.getElementById('dbName').value,
            username: document.getElementById('dbUsername').value,
            password: document.getElementById('dbPassword').value
        };
    },
    
    async testConnection() {
        const dbInfo = this.getDbInfo();
        if (!dbInfo.host || !dbInfo.port || !dbInfo.databaseName || !dbInfo.username) {
            toast.error('请填写完整的数据库连接信息');
            return;
        }
        
        try {
            const result = await api.post('/api/database/test', dbInfo);
            if (result.success && result.data?.success) {
                toast.success('连接成功！');
            } else {
                toast.error('连接失败：' + (result.message || '请检查配置'));
            }
        } catch (e) {
            toast.error('连接失败：' + e.message);
        }
    },
    
    async connectDatabase() {
        const dbInfo = this.getDbInfo();
        if (!dbInfo.host || !dbInfo.port || !dbInfo.databaseName || !dbInfo.username) {
            toast.error('请填写完整的数据库连接信息');
            return;
        }
        
        try {
            const result = await api.post('/api/database/connect', dbInfo);
            if (result.success && result.data?.success) {
                this.dbInfo = dbInfo;
                this.allTables = result.data.tables || [];
                toast.success('连接成功！');
                this.updateDatabaseStatus();
            } else {
                toast.error('连接失败：' + (result.message || '请检查配置'));
            }
        } catch (e) {
            toast.error('连接失败：' + e.message);
        }
    },
    
    updateDatabaseStatus() {
        const status = document.getElementById('databaseStatus');
        if (this.dbInfo.host) {
            status.innerHTML = `
                <span style="color: var(--success);">●</span>
                ${this.dbInfo.host}:${this.dbInfo.port}/${this.dbInfo.databaseName}
            `;
        }
    },
    
    showTableSelector() {
        if (!this.allTables || this.allTables.length === 0) {
            toast.warning('请先连接数据库');
            return;
        }
        
        const html = `
            <div class="modal-header">
                <h3 class="modal-title">选择数据表</h3>
                <button class="modal-close" onclick="modal.hide()">&times;</button>
            </div>
            <div class="mb-16">
                <div class="flex gap-12 mb-16">
                    <input type="text" class="input" id="tableSearchInput" placeholder="搜索表名..." oninput="app.filterTables()">
                    <button class="btn btn-secondary" onclick="app.selectAllTables()">全选</button>
                    <button class="btn btn-secondary" onclick="app.deselectAllTables()">取消全选</button>
                </div>
                <div class="table-list" id="tableList">
                    ${this.allTables.map(table => `
                        <div class="table-item ${this.selectedTables.includes(table) ? 'selected' : ''}" 
                             onclick="app.toggleTable('${table}')">
                            <input type="checkbox" ${this.selectedTables.includes(table) ? 'checked' : ''}>
                            <span>${table}</span>
                        </div>
                    `).join('')}
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="modal.hide()">取消</button>
                <button class="btn btn-primary" onclick="app.confirmTableSelection()">确定 (${this.selectedTables.length})</button>
            </div>
        `;
        modal.show(html);
    },
    
    filterTables() {
        const search = document.getElementById('tableSearchInput').value.toLowerCase();
        const items = document.querySelectorAll('.table-item');
        items.forEach(item => {
            const tableName = item.querySelector('span').textContent.toLowerCase();
            item.style.display = tableName.includes(search) ? 'flex' : 'none';
        });
    },
    
    selectAllTables() {
        this.selectedTables = [...this.allTables];
        this.showTableSelector();
    },
    
    deselectAllTables() {
        this.selectedTables = [];
        this.showTableSelector();
    },
    
    toggleTable(table) {
        const index = this.selectedTables.indexOf(table);
        if (index >= 0) {
            this.selectedTables.splice(index, 1);
        } else {
            this.selectedTables.push(table);
        }
        this.showTableSelector();
    },
    
    confirmTableSelection() {
        document.getElementById('tablesInput').value = this.selectedTables.join('\n');
        modal.hide();
    },
    
    getExportConfig() {
        const config = { ...this.exportConfig };
        
        config.tables = document.getElementById('tablesInput').value;
        config.customSql = document.getElementById('customSqlInput').value;
        
        config.enableTimeFilter = document.getElementById('enableTimeFilter').checked;
        config.timeFieldNames = document.getElementById('timeFieldNames').value;
        config.startDate = document.getElementById('startDate').value;
        config.endDate = document.getElementById('endDate').value;
        
        config.enableFieldFilter = document.getElementById('enableFieldFilter').checked;
        config.fieldFilter = {
            fieldName: document.getElementById('fieldName').value,
            filterType: document.getElementById('fieldFilterType').value,
            filterValue: document.getElementById('fieldFilterValue').value,
            enabled: config.enableFieldFilter
        };
        
        const formats = [];
        if (document.getElementById('formatExcel').checked) formats.push('excel');
        if (document.getElementById('formatSql').checked) formats.push('sql');
        config.exportFormats = formats.join(',');
        
        config.maxConnections = parseInt(document.getElementById('maxConnections').value);
        
        return config;
    },
    
    async startExport() {
        const dbInfo = this.getDbInfo();
        const config = this.getExportConfig();
        
        if (!dbInfo.host || !dbInfo.port || !dbInfo.databaseName || !dbInfo.username) {
            toast.error('请填写完整的数据库连接信息');
            return;
        }
        
        if (config.exportType !== 'customSql' && !config.tables.trim()) {
            toast.error('请选择或输入要导出的表');
            return;
        }
        
        if (config.exportType === 'customSql' && !config.customSql.trim()) {
            toast.error('请输入SQL语句');
            return;
        }
        
        if (!config.exportFormats) {
            toast.error('请选择导出格式');
            return;
        }
        
        try {
            const result = await api.post('/api/export/start', {
                database: dbInfo,
                config: config
            });
            
            if (result.success) {
                this.currentTaskId = result.data.taskId;
                this.showProgressModal();
                this.startProgressPolling();
            } else {
                toast.error('启动导出失败：' + result.message);
            }
        } catch (e) {
            toast.error('启动导出失败：' + e.message);
        }
    },
    
    showProgressModal() {
        const html = `
            <div class="modal-header">
                <h3 class="modal-title">导出进度</h3>
                <button class="modal-close" onclick="modal.hide()">&times;</button>
            </div>
            <div id="progressContent">
                <div class="progress-bar mb-16">
                    <div class="progress-fill" id="progressFill" style="width: 0%">0%</div>
                </div>
                <div class="mb-16">
                    <div>当前表：<span id="currentTable">-</span></div>
                    <div>已完成：<span id="completedTables">0</span>/<span id="totalTables">0</span> 个表</div>
                    <div>总数据：<span id="totalRows">0</span> 条</div>
                    <div>已用时：<span id="elapsedTime">0s</span></div>
                    <div>预计剩余：<span id="remainingTime">-</span></div>
                </div>
                <div class="log-area" id="logArea"></div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" id="cancelExportBtn" onclick="app.cancelExport()">取消导出</button>
                <button class="btn btn-primary" id="downloadExportBtn" style="display:none;" onclick="app.downloadCurrentFile()">下载</button>
                <button class="btn btn-secondary" id="closeProgressBtn" style="display:none;" onclick="modal.hide()">关闭</button>
            </div>
        `;
        modal.show(html);
    },
    
    async startProgressPolling() {
        if (this.progressInterval) clearInterval(this.progressInterval);
        
        this.progressInterval = setInterval(async () => {
            try {
                const result = await api.get(`/api/export/progress/${this.currentTaskId}`);
                if (result.success && result.data) {
                    this.updateProgress(result.data);
                }
            } catch (e) {
                console.error(e);
            }
        }, 1000);
    },
    
    updateProgress(progress) {
        const percent = Math.round(progress.progressPercent || 0);
        document.getElementById('progressFill').style.width = `${percent}%`;
        document.getElementById('progressFill').textContent = `${percent}%`;
        
        document.getElementById('currentTable').textContent = progress.currentTable || '-';
        document.getElementById('completedTables').textContent = progress.completedTables || 0;
        document.getElementById('totalTables').textContent = progress.totalTables || 0;
        document.getElementById('totalRows').textContent = progress.totalRows || 0;
        document.getElementById('elapsedTime').textContent = util.formatDuration(progress.elapsedTime);
        document.getElementById('remainingTime').textContent = util.formatDuration(progress.estimatedRemainingTime);
        
        const logArea = document.getElementById('logArea');
        if (progress.logs) {
            logArea.innerHTML = progress.logs.map(log => `
                <div class="log-item log-${log.level.toLowerCase()}">
                    [${util.formatDate(log.timestamp)}] [${log.level.toUpperCase()}] ${log.message}
                </div>
            `).join('');
            logArea.scrollTop = logArea.scrollHeight;
        }
        
        if (progress.status === 'completed' || progress.status === 'failed') {
            clearInterval(this.progressInterval);
            document.getElementById('cancelExportBtn').style.display = 'none';
            document.getElementById('closeProgressBtn').style.display = 'inline-flex';
            
            if (progress.status === 'completed' && progress.fileName) {
                document.getElementById('downloadExportBtn').style.display = 'inline-flex';
                toast.success('导出完成！');
                this.loadExportFiles();
            } else if (progress.status === 'failed') {
                toast.error('导出失败');
            }
        }
    },
    
    async cancelExport() {
        if (!this.currentTaskId) return;
        try {
            await api.post(`/api/export/cancel/${this.currentTaskId}`);
            toast.warning('已取消导出');
            clearInterval(this.progressInterval);
        } catch (e) {
            toast.error('取消失败：' + e.message);
        }
    },
    
    downloadCurrentFile() {
        window.location.href = `/api/export/download/${this.currentFileName}`;
    },
    
    async loadTemplates() {
        try {
            const result = await api.get('/api/templates');
            if (result.success) {
                this.templates = result.data || [];
                this.renderTemplates();
            }
        } catch (e) {
            console.error(e);
        }
    },
    
    renderTemplates() {
        const container = document.getElementById('templateList');
        if (!container) return;
        
        if (this.templates.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">📋</div>
                    <div class="empty-title">暂无模板</div>
                    <div class="empty-desc">在数据导出页面配置后保存为模板</div>
                </div>
            `;
            return;
        }
        
        container.innerHTML = `
            <div class="flex justify-between gap-12 mb-16">
                <div class="flex gap-12">
                    <button class="btn btn-secondary" onclick="app.exportTemplates()">导出选中</button>
                    <input type="file" id="templateImportInput" style="display:none;" accept=".zip" onchange="app.importTemplates(event)">
                    <button class="btn btn-secondary" onclick="document.getElementById('templateImportInput').click()">导入模板</button>
                </div>
            </div>
            <table class="table">
                <thead>
                    <tr>
                        <th><input type="checkbox" id="selectAllTemplates"></th>
                        <th>模板名称</th>
                        <th>描述</th>
                        <th>创建时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    ${this.templates.map(template => `
                        <tr>
                            <td><input type="checkbox" class="template-checkbox" data-id="${template.id}"></td>
                            <td>${template.name}</td>
                            <td>${template.description || '-'}</td>
                            <td>${util.formatDate(template.createTime)}</td>
                            <td>
                                <button class="btn btn-secondary" onclick="app.loadTemplate(${template.id})">加载</button>
                                <button class="btn btn-danger" onclick="app.deleteTemplate(${template.id})">删除</button>
                            </td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;
    },
    
    showSaveTemplateModal() {
        const html = `
            <div class="modal-header">
                <h3 class="modal-title">保存为模板</h3>
                <button class="modal-close" onclick="modal.hide()">&times;</button>
            </div>
            <div class="mb-16">
                <div class="form-group">
                    <label class="label">模板名称</label>
                    <input type="text" class="input" id="templateName" placeholder="请输入模板名称">
                </div>
                <div class="form-group">
                    <label class="label">描述</label>
                    <input type="text" class="input" id="templateDesc" placeholder="请输入描述">
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="modal.hide()">取消</button>
                <button class="btn btn-primary" onclick="app.saveTemplate()">保存</button>
            </div>
        `;
        modal.show(html);
    },
    
    async saveTemplate() {
        const name = document.getElementById('templateName').value;
        const description = document.getElementById('templateDesc').value;
        
        if (!name.trim()) {
            toast.error('请输入模板名称');
            return;
        }
        
        const config = this.getExportConfig();
        const template = {
            name,
            description,
            configJson: JSON.stringify(config)
        };
        
        try {
            const result = await api.post('/api/templates', template);
            if (result.success) {
                toast.success('模板保存成功！');
                this.loadTemplates();
                modal.hide();
            } else {
                toast.error('保存失败：' + result.message);
            }
        } catch (e) {
            toast.error('保存失败：' + e.message);
        }
    },
    
    showLoadTemplateModal() {
        if (this.templates.length === 0) {
            toast.warning('暂无模板');
            return;
        }
        
        const html = `
            <div class="modal-header">
                <h3 class="modal-title">选择模板</h3>
                <button class="modal-close" onclick="modal.hide()">&times;</button>
            </div>
            <div class="mb-16">
                ${this.templates.map(template => `
                    <div class="table-item" onclick="app.loadTemplateAndClose(${template.id})">
                        <div style="flex:1">
                            <div style="font-weight:500">${template.name}</div>
                            <div class="text-small text-muted">${template.description || '无描述'}</div>
                        </div>
                        <div class="text-small text-muted">${util.formatDate(template.createTime)}</div>
                    </div>
                `).join('')}
            </div>
        `;
        modal.show(html);
    },
    
    async loadTemplate(id) {
        try {
            const result = await api.get(`/api/templates/${id}`);
            if (result.success && result.data) {
                this.applyTemplate(result.data);
                toast.success('模板已加载');
                modal.hide();
                this.switchPage('export');
            }
        } catch (e) {
            toast.error('加载失败：' + e.message);
        }
    },
    
    async loadTemplateAndClose(id) {
        await this.loadTemplate(id);
    },
    
    applyTemplate(template) {
        try {
            const config = JSON.parse(template.configJson);
            
            this.exportConfig = { ...this.exportConfig, ...config };
            
            if (config.exportType) {
                document.querySelectorAll('.export-type-tab').forEach(tab => {
                    tab.classList.toggle('active', tab.dataset.type === config.exportType);
                });
                document.querySelectorAll('.export-type-content').forEach(c => c.style.display = 'none');
                document.getElementById(`${config.exportType}Content`).style.display = 'block';
            }
            
            if (config.tables) document.getElementById('tablesInput').value = config.tables;
            if (config.customSql) document.getElementById('customSqlInput').value = config.customSql;
            
            document.getElementById('enableTimeFilter').checked = config.enableTimeFilter;
            document.getElementById('timeFilterSection').style.display = config.enableTimeFilter ? 'block' : 'none';
            if (config.timeFieldNames) document.getElementById('timeFieldNames').value = config.timeFieldNames;
            if (config.startDate) document.getElementById('startDate').value = config.startDate;
            if (config.endDate) document.getElementById('endDate').value = config.endDate;
            
            document.getElementById('enableFieldFilter').checked = config.enableFieldFilter;
            document.getElementById('fieldFilterSection').style.display = config.enableFieldFilter ? 'block' : 'none';
            if (config.fieldFilter) {
                if (config.fieldFilter.fieldName) document.getElementById('fieldName').value = config.fieldFilter.fieldName;
                if (config.fieldFilter.filterType) document.getElementById('fieldFilterType').value = config.fieldFilter.filterType;
                if (config.fieldFilter.filterValue) document.getElementById('fieldFilterValue').value = config.fieldFilter.filterValue;
            }
            
            if (config.exportFormats) {
                const formats = config.exportFormats.split(',');
                document.getElementById('formatExcel').checked = formats.includes('excel');
                document.getElementById('formatSql').checked = formats.includes('sql');
            }
            
            if (config.maxConnections) document.getElementById('maxConnections').value = config.maxConnections;
            
        } catch (e) {
            toast.error('解析模板失败');
        }
    },
    
    async deleteTemplate(id) {
        if (!confirm('确定要删除此模板吗？')) return;
        
        try {
            const result = await api.delete(`/api/templates/${id}`);
            if (result.success) {
                toast.success('删除成功');
                this.loadTemplates();
            } else {
                toast.error('删除失败：' + result.message);
            }
        } catch (e) {
            toast.error('删除失败：' + e.message);
        }
    },
    
    async exportTemplates() {
        const selected = [];
        document.querySelectorAll('.template-checkbox:checked').forEach(cb => {
            selected.push(parseInt(cb.dataset.id));
        });
        
        if (selected.length === 0) {
            toast.warning('请先选择要导出的模板');
            return;
        }
        
        try {
            const response = await fetch('/api/templates/export', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(selected)
            });
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `templates_${Date.now()}.zip`;
            a.click();
            window.URL.revokeObjectURL(url);
            toast.success('导出成功');
        } catch (e) {
            toast.error('导出失败：' + e.message);
        }
    },
    
    async importTemplates(event) {
        const file = event.target.files[0];
        if (!file) return;
        
        const formData = new FormData();
        formData.append('file', file);
        
        try {
            const response = await fetch('/api/templates/import', {
                method: 'POST',
                body: formData
            });
            const result = await response.json();
            if (result.success) {
                toast.success(`成功导入 ${result.data?.length || 0} 个模板`);
                this.loadTemplates();
            } else {
                toast.error('导入失败：' + result.message);
            }
        } catch (e) {
            toast.error('导入失败：' + e.message);
        }
        
        event.target.value = '';
    },
    
    async loadExportFiles() {
        try {
            const result = await api.get('/api/export/files');
            if (result.success) {
                this.exportFiles = result.data || [];
                this.renderExportFiles();
            }
        } catch (e) {
            console.error(e);
        }
    },
    
    renderExportFiles() {
        const container = document.getElementById('exportFileList');
        if (!container) return;
        
        if (this.exportFiles.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">📦</div>
                    <div class="empty-title">暂无导出记录</div>
                    <div class="empty-desc">开始导出数据后，记录将显示在这里</div>
                </div>
            `;
            return;
        }
        
        container.innerHTML = this.exportFiles.map(file => `
            <div class="file-item">
                <div class="file-info">
                    <div class="file-name">${file.fileName}</div>
                    <div class="file-meta">
                        ${util.formatFileSize(file.fileSize)} · ${util.formatDate(file.createTime)}
                    </div>
                </div>
                <div class="flex gap-8">
                    <button class="btn btn-primary" onclick="app.downloadFile('${file.fileName}')">下载</button>
                    <button class="btn btn-danger" onclick="app.deleteFile('${file.fileName}')">删除</button>
                </div>
            </div>
        `).join('');
    },
    
    downloadFile(fileName) {
        window.location.href = `/api/export/download/${fileName}`;
    },
    
    async deleteFile(fileName) {
        if (!confirm('确定要删除此文件吗？')) return;
        
        try {
            const result = await api.delete(`/api/export/files/${fileName}`);
            if (result.success) {
                toast.success('删除成功');
                this.loadExportFiles();
            } else {
                toast.error('删除失败：' + result.message);
            }
        } catch (e) {
            toast.error('删除失败：' + e.message);
        }
    }
};

const loginApp = {
    async init() {
        this.bindEvents();
    },
    
    bindEvents() {
        const form = document.getElementById('loginForm');
        if (form) {
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                this.login();
            });
        }
    },
    
    async login() {
        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;
        
        if (!username || !password) {
            toast.error('请输入用户名和密码');
            return;
        }
        
        try {
            const result = await api.post('/api/login', { username, password });
            if (result.success) {
                window.location.href = '/index.html';
            } else {
                toast.error('登录失败：' + result.message);
            }
        } catch (e) {
            toast.error('登录失败：' + e.message);
        }
    }
};

document.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('loginPage')) {
        loginApp.init();
    } else {
        app.init();
    }
});
