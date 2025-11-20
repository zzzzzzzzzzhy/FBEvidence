class EvidenceQuery {
    constructor() {
        this.currentPage = 1;
        this.pageSize = 10;
        this.total = 0;
        this.searchForm = document.getElementById('searchForm');
        this.resultsTable = document.getElementById('resultsTable');
        this.resultsBody = document.getElementById('resultsBody');
        this.pagination = document.getElementById('pagination');

        this.selectedEvidence = null;

        this.initEvents();
        this.checkAuthToken();
        this.loadInitialData();
    }

    initEvents() {
        // 搜索表单提交
        this.searchForm.addEventListener('submit', (e) => {
            e.preventDefault();
            this.currentPage = 1;
            this.searchEvidence();
        });

        // 重置表单
        this.searchForm.addEventListener('reset', (e) => {
            setTimeout(() => {
                this.currentPage = 1;
                this.searchEvidence();
            }, 100);
        });
    }

    checkAuthToken() {
        const token = localStorage.getItem('token');
        if (!token) {
            alert('请先登录系统');
            window.location.href = '/';
        }
    }

    loadInitialData() {
        this.searchEvidence();
    }

    async searchEvidence() {
        const params = this.getSearchParams();

        try {
            this.showLoading();
            const response = await this.fetchEvidence(params);
            this.displayResults(response.data.records);
            this.updatePagination(response.data);
        } catch (error) {
            this.showError(error.message);
        } finally {
            this.hideLoading();
        }
    }

    getSearchParams() {
        const formData = new FormData(this.searchForm);
        const params = {
            current: this.currentPage,
            size: this.pageSize
        };

        // 收集表单数据
        for (let [key, value] of formData.entries()) {
            if (value.trim()) {
                params[key] = value.trim();
            }
        }

        return params;
    }

    async fetchEvidence(params) {
        const token = localStorage.getItem('token');
        const queryString = new URLSearchParams(params).toString();

        const response = await fetch(`/api/evidence/list?${queryString}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error(`查询失败：HTTP ${response.status}`);
        }

        const result = await response.json();
        if (result.code !== 200) {
            throw new Error(result.message || '查询失败');
        }

        return result;
    }

    displayResults(records) {
        if (!records || records.length === 0) {
            this.resultsBody.innerHTML = '<tr class="no-data"><td colspan="9">暂无数据</td></tr>';
            return;
        }

        const rows = records.map((record, index) => {
            const rowIndex = (this.currentPage - 1) * this.pageSize + index + 1;
            return this.createTableRow(record, rowIndex);
        }).join('');

        this.resultsBody.innerHTML = rows;
    }

    createTableRow(record, index) {
        const statusClass = this.getStatusClass(record.chainStatus);
        const statusText = this.getStatusText(record.chainStatus);

        return `
            <tr>
                <td>${index}</td>
                <td title="${record.fileName}">${this.truncateText(record.fileName, 20)}</td>
                <td title="${record.fileHash}" class="hash-cell">${this.truncateText(record.fileHash, 16)}</td>
                <td>${this.formatFileSize(record.fileSize)}</td>
                <td><span class="status-tag ${statusClass}">${statusText}</span></td>
                <td title="${record.transactionHash || ''}" class="hash-cell">
                    ${record.transactionHash ? this.truncateText(record.transactionHash, 16) : '-'}
                </td>
                <td>${record.blockNumber || '-'}</td>
                <td>${this.formatDate(record.createdAt)}</td>
                <td class="actions">
                    <button class="btn btn-sm btn-info" onclick="evidenceQuery.viewDetail(${record.id})">详情</button>
                    <button class="btn btn-sm btn-success" onclick="evidenceQuery.verifyEvidence('${record.fileHash}')">验证</button>
                </td>
            </tr>
        `;
    }

    updatePagination(data) {
        this.total = data.total;
        const totalPages = Math.ceil(this.total / this.pageSize);

        if (totalPages <= 1) {
            this.pagination.innerHTML = '';
            return;
        }

        let paginationHtml = '<div class="pagination-info">共 ' + this.total + ' 条记录</div>';
        paginationHtml += '<div class="pagination-controls">';

        // 上一页
        if (this.currentPage > 1) {
            paginationHtml += `<button onclick="evidenceQuery.goToPage(${this.currentPage - 1})">上一页</button>`;
        }

        // 页码
        const startPage = Math.max(1, this.currentPage - 2);
        const endPage = Math.min(totalPages, this.currentPage + 2);

        for (let i = startPage; i <= endPage; i++) {
            const activeClass = i === this.currentPage ? 'active' : '';
            paginationHtml += `<button class="${activeClass}" onclick="evidenceQuery.goToPage(${i})">${i}</button>`;
        }

        // 下一页
        if (this.currentPage < totalPages) {
            paginationHtml += `<button onclick="evidenceQuery.goToPage(${this.currentPage + 1})">下一页</button>`;
        }

        paginationHtml += '</div>';
        this.pagination.innerHTML = paginationHtml;
    }

    goToPage(page) {
        this.currentPage = page;
        this.searchEvidence();
    }

    async viewDetail(id) {
        try {
            const evidence = await this.fetchEvidenceDetail(id);
            this.showDetailModal(evidence);
        } catch (error) {
            alert('获取详情失败：' + error.message);
        }
    }

    async fetchEvidenceDetail(id) {
        const token = localStorage.getItem('token');

        const response = await fetch(`/api/evidence/${id}`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const result = await response.json();
        if (result.code !== 200) {
            throw new Error(result.message);
        }

        return result.data;
    }

    showDetailModal(evidence) {
        this.selectedEvidence = evidence;

        const detailContent = document.getElementById('detailContent');
        detailContent.innerHTML = `
            <div class="detail-grid">
                <div class="detail-item">
                    <label>文件名：</label>
                    <span>${evidence.fileName}</span>
                </div>
                <div class="detail-item">
                    <label>文件大小：</label>
                    <span>${this.formatFileSize(evidence.fileSize)}</span>
                </div>
                <div class="detail-item full-width">
                    <label>文件哈希：</label>
                    <span class="hash-value">${evidence.fileHash}</span>
                    <button onclick="evidenceQuery.copyToClipboard('${evidence.fileHash}')">复制</button>
                </div>
                <div class="detail-item">
                    <label>哈希算法：</label>
                    <span>${evidence.hashAlgorithm}</span>
                </div>
                <div class="detail-item">
                    <label>状态：</label>
                    <span class="status-tag ${this.getStatusClass(evidence.chainStatus)}">
                        ${this.getStatusText(evidence.chainStatus)}
                    </span>
                </div>
                <div class="detail-item full-width">
                    <label>描述：</label>
                    <span>${evidence.description || '无'}</span>
                </div>
                ${evidence.transactionHash ? `
                <div class="detail-item full-width">
                    <label>交易哈希：</label>
                    <span class="hash-value">${evidence.transactionHash}</span>
                    <button onclick="evidenceQuery.copyToClipboard('${evidence.transactionHash}')">复制</button>
                </div>
                ` : ''}
                <div class="detail-item">
                    <label>区块号：</label>
                    <span>${evidence.blockNumber || '未知'}</span>
                </div>
                <div class="detail-item">
                    <label>创建时间：</label>
                    <span>${this.formatDate(evidence.createdAt)}</span>
                </div>
                <div class="detail-item">
                    <label>更新时间：</label>
                    <span>${this.formatDate(evidence.updatedAt)}</span>
                </div>
            </div>
        `;

        document.getElementById('detailModal').style.display = 'block';
    }

    async verifyEvidence(fileHash) {
        if (!fileHash) {
            alert('文件哈希不能为空');
            return;
        }

        try {
            const result = await this.performVerification(fileHash);
            if (result) {
                alert('✅ 验证成功！\n文件确实存在于区块链上，数据完整未被篡改。');
            } else {
                alert('❌ 验证失败！\n文件可能不存在于区块链上或数据已被篡改。');
            }
        } catch (error) {
            alert('验证过程出错：' + error.message);
        }
    }

    async performVerification(fileHash) {
        const token = localStorage.getItem('token');

        const response = await fetch('/api/evidence/verify', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: `fileHash=${encodeURIComponent(fileHash)}`
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const result = await response.json();
        if (result.code !== 200) {
            throw new Error(result.message);
        }

        return result.data;
    }

    // 工具方法
    getStatusClass(status) {
        const statusMap = {
            0: 'status-warning',
            1: 'status-success',
            2: 'status-danger'
        };
        return statusMap[status] || 'status-default';
    }

    getStatusText(status) {
        const statusMap = {
            0: '待上链',
            1: '上链成功',
            2: '上链失败'
        };
        return statusMap[status] || '未知状态';
    }

    formatFileSize(bytes) {
        if (!bytes) return '0B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + sizes[i];
    }

    formatDate(dateString) {
        if (!dateString) return '';
        return new Date(dateString).toLocaleString('zh-CN');
    }

    truncateText(text, length) {
        if (!text) return '';
        return text.length > length ? text.substring(0, length) + '...' : text;
    }

    copyToClipboard(text) {
        if (navigator.clipboard) {
            navigator.clipboard.writeText(text).then(() => {
                alert('已复制到剪贴板');
            });
        } else {
            // 兜底方案
            const textArea = document.createElement('textarea');
            textArea.value = text;
            document.body.appendChild(textArea);
            textArea.select();
            document.execCommand('copy');
            document.body.removeChild(textArea);
            alert('已复制到剪贴板');
        }
    }

    showLoading() {
        this.resultsBody.innerHTML = '<tr><td colspan="9" class="loading">查询中...</td></tr>';
    }

    hideLoading() {
        // Loading状态在displayResults中处理
    }

    showError(message) {
        this.resultsBody.innerHTML = `<tr><td colspan="9" class="error">查询失败: ${message}</td></tr>`;
    }
}

// 全局函数
function showQuickVerify() {
    document.getElementById('quickVerifyModal').style.display = 'block';
}

function closeQuickVerify() {
    document.getElementById('quickVerifyModal').style.display = 'none';
    document.getElementById('verifyHash').value = '';
    document.getElementById('verifyResult').style.display = 'none';
}

function doQuickVerify() {
    const hash = document.getElementById('verifyHash').value.trim();
    if (!hash) {
        alert('请输入文件哈希值');
        return;
    }
    evidenceQuery.verifyEvidence(hash);
}

function closeDetail() {
    document.getElementById('detailModal').style.display = 'none';
}

function verifyFromDetail() {
    if (evidenceQuery.selectedEvidence) {
        evidenceQuery.verifyEvidence(evidenceQuery.selectedEvidence.fileHash);
    }
}

function viewOnChain() {
    if (evidenceQuery.selectedEvidence && evidenceQuery.selectedEvidence.transactionHash) {
        window.open(`/blockchain-explorer?tx=${evidenceQuery.selectedEvidence.transactionHash}`, '_blank');
    } else {
        alert('该文件尚未上链');
    }
}

function refreshResults() {
    evidenceQuery.searchEvidence();
}

function exportResults() {
    alert('导出功能开发中...');
}

// 初始化
let evidenceQuery;
document.addEventListener('DOMContentLoaded', () => {
    evidenceQuery = new EvidenceQuery();
});