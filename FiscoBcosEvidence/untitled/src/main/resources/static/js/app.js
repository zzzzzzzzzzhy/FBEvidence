// src/main/resources/static/js/app.js

// Vue应用主文件
new Vue({
    el: '#app',
    data() {
        return {
            // 用户相关
            userInfo: null,
            userInfoVisible: false,

            // 登录表单
            loginForm: {
                username: '',
                password: ''
            },
            loginRules: {
                username: [
                    { required: true, message: '请输入用户名', trigger: 'blur' },
                    { min: 3, max: 50, message: '用户名长度在 3 到 50 个字符', trigger: 'blur' }
                ],
                password: [
                    { required: true, message: '请输入密码', trigger: 'blur' },
                    { min: 6, message: '密码长度至少 6 个字符', trigger: 'blur' }
                ]
            },
            loginLoading: false,

            // 注册表单
            registerForm: {
                username: '',
                realName: '',
                email: '',
                password: '',
                confirmPassword: ''
            },
            registerRules: {
                username: [
                    { required: true, message: '请输入用户名', trigger: 'blur' },
                    { min: 3, max: 50, message: '用户名长度在 3 到 50 个字符', trigger: 'blur' }
                ],
                realName: [
                    { required: true, message: '请输入真实姓名', trigger: 'blur' }
                ],
                email: [
                    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
                    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
                ],
                password: [
                    { required: true, message: '请输入密码', trigger: 'blur' },
                    { min: 6, message: '密码长度至少 6 个字符', trigger: 'blur' }
                ],
                confirmPassword: [
                    { required: true, message: '请再次输入密码', trigger: 'blur' },
                    { validator: this.validateConfirmPassword, trigger: 'blur' }
                ]
            },
            registerLoading: false,
            showRegister: false,

            // 导航相关
            activeIndex: '1',
            activeTab: 'upload',

            // 上传相关
            uploadAction: '/api/evidence/upload',
            uploadHeaders: {},
            uploadData: {
                description: '',
                hashAlgorithm: 'SHA256'
            },
            fileList: [],
            uploadLoading: false,
            recentUploads: [],
            recentLoading: false,

            // 文件存证管理相关
            fileEvidenceActiveView: 'upload',
            selectedGroupId: null,
            selectedProjectId: null,
            userGroups: [],
            currentProjects: [],
            batchCommitMessage: '',
            fileEvidenceHashAlgorithm: 'SHA256',
            fileEvidenceDescription: '',

            // 查询相关
            queryForm: {
                fileName: '',
                fileHash: '',
                transactionHash: '',
                blockNumber: '',
                chainStatus: null,
                current: 1,
                size: 10
            },
            dateRange: null,

            // 表格数据
            evidenceList: [],
            tableLoading: false,

            // 分页
            pagination: {
                current: 1,
                size: 10,
                total: 0
            },

            // 对话框
            detailDialogVisible: false,
            selectedEvidence: null,
            quickVerifyVisible: false,
            quickVerifyHash: '',

            // 区块链信息
            blockchainInfo: {
                blockNumber: null,
                connected: false,
                groupId: 'group0'
            },
            contractAddress: '',
            statistics: {
                totalFiles: 0,
                todayFiles: 0,
                successRate: '0%'
            },
            searchType: 'block',
            searchValue: '',
            blockchainSearchResult: null,

            // 代码存证相关
            repositories: [],
            branches: [],
            selectedRepository: '',
            repositoryForm: {
                groupName: '',
                projectName: '',
                description: ''
            },
            repositoryRules: {
                groupName: [
                    { required: true, message: '请输入分组名称', trigger: 'blur' },
                    { pattern: /^[a-zA-Z0-9_-]+$/, message: '只能包含字母、数字、下划线和连字符', trigger: 'blur' },
                    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
                ],
                projectName: [
                    { required: true, message: '请输入项目名称', trigger: 'blur' },
                    { pattern: /^[a-zA-Z0-9_-]+$/, message: '只能包含字母、数字、下划线和连字符', trigger: 'blur' },
                    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
                ]
            },
            branchForm: {
                branchName: '',
                baseBranch: ''
            },
            codeUploadForm: {
                repositoryId: '',
                branchName: 'main',
                commitMessage: ''
            },
            codeFileList: [],
            codeEvidenceList: [],
            codeLoading: {
                createRepo: false,
                createBranch: false,
                upload: false,
                table: false
            },
            codeStats: {
                totalRepositories: 0,
                totalBranches: 0,
                totalCommits: 0,
                todayCommits: 0
            }
        }
    },

    computed: {
        // 计算当前页面标题
        pageTitle() {
            const titles = {
                'upload': '文件存证',
                'code': '代码存证',
                'query': '查询验证',
                'blockchain': '区块链信息'
            };
            return titles[this.activeTab] || '区块链存证系统';
        }
    },

    watch: {
        // 监听activeTab变化，同步activeIndex并加载数据
        activeTab(newVal, oldVal) {
            console.log('[DEBUG] watch activeTab: 从', oldVal, '切换到', newVal);

            const indexMap = {
                'upload': '1',
                'code': '2',
                'query': '3',
                'blockchain': '4'
            };
            this.activeIndex = indexMap[newVal] || '1';

            // 只有当用户已登录且标签页确实发生变化时才加载数据
            if (this.userInfo && newVal !== oldVal) {
                console.log('[DEBUG] watch activeTab: 触发数据加载');
                this.loadTabData(newVal);
            }
        },

        // 监听日期范围变化
        dateRange(newVal) {
            if (newVal && newVal.length === 2) {
                this.queryForm.startDate = this.formatDateForQuery(newVal[0]);
                this.queryForm.endDate = this.formatDateForQuery(newVal[1]);
            } else {
                this.queryForm.startDate = '';
                this.queryForm.endDate = '';
            }
        }
    },

    created() {
        this.initializeApp();
    },

    mounted() {
        // 设置页面标题
        document.title = this.pageTitle;

        // 监听浏览器前进后退
        window.addEventListener('popstate', this.handlePopState);

        // 监听网络状态
        window.addEventListener('online', this.handleOnline);
        window.addEventListener('offline', this.handleOffline);
    },

    beforeDestroy() {
        // 清理事件监听器
        window.removeEventListener('popstate', this.handlePopState);
        window.removeEventListener('online', this.handleOnline);
        window.removeEventListener('offline', this.handleOffline);
    },

    methods: {
        // ==================== 验证方法 ====================

        // 验证确认密码
        validateConfirmPassword(rule, value, callback) {
            if (value === '') {
                callback(new Error('请再次输入密码'));
            } else if (value !== this.registerForm.password) {
                callback(new Error('两次输入密码不一致!'));
            } else {
                callback();
            }
        },

        // ==================== 初始化方法 ====================

        // 初始化应用
        initializeApp() {
            this.setupAxios();
            this.checkLogin();
            this.loadInitialData();
        },

        // 设置axios拦截器
        setupAxios() {
            axios.defaults.baseURL = '';
            axios.defaults.timeout = 30000;

            // 请求拦截器
            axios.interceptors.request.use(
                config => {
                    const token = localStorage.getItem('token');
                    if (token) {
                        config.headers.Authorization = `Bearer ${token}`;
                        this.uploadHeaders.Authorization = `Bearer ${token}`;
                    }

                    // 显示加载状态
                    if (config.showLoading !== false) {
                        this.showGlobalLoading();
                    }

                    return config;
                },
                error => {
                    this.hideGlobalLoading();
                    return Promise.reject(error);
                }
            );

            // 响应拦截器
            axios.interceptors.response.use(
                response => {
                    this.hideGlobalLoading();
                    const data = response.data;

                    if (data.code !== 200) {
                        this.$message.error(data.message || '操作失败');
                        return Promise.reject(data);
                    }

                    return data.data;
                },
                error => {
                    this.hideGlobalLoading();

                    if (error.response?.status === 401) {
                        this.handleUnauthorized();
                    } else if (error.response?.status === 403) {
                        this.$message.error('没有权限访问该资源');
                    } else if (error.response?.status === 500) {
                        this.$message.error('服务器内部错误，请稍后重试');
                    } else if (error.code === 'ECONNABORTED') {
                        this.$message.error('请求超时，请检查网络连接');
                    } else {
                        this.$message.error(error.response?.data?.message || '网络错误，请检查连接');
                    }

                    return Promise.reject(error);
                }
            );
        },

        // 检查登录状态
        checkLogin() {
            const token = localStorage.getItem('token');
            const userInfo = localStorage.getItem('userInfo');

            if (token && userInfo) {
                try {
                    this.userInfo = JSON.parse(userInfo);
                    this.loadInitialData();
                } catch (e) {
                    console.error('解析用户信息失败:', e);
                    this.logout();
                }
            }
        },

        // 加载初始数据
        loadInitialData() {
            if (this.userInfo) {
                console.log('[DEBUG] loadInitialData: 开始加载初始数据，当前标签页:', this.activeTab);

                // 加载当前标签页的数据
                this.loadTabData(this.activeTab);

                // 可选：预加载一些通用数据（如统计信息）
                this.loadStatistics().catch(error => {
                    console.error('加载统计信息失败:', error);
                });
            }
        },

        // ==================== 认证相关方法 ====================

        // 用户登录
        handleLogin() {
            this.$refs.loginForm.validate(valid => {
                if (!valid) return;

                this.loginLoading = true;

                axios.post('/api/auth/login', this.loginForm)
                    .then(data => {
                        // 保存登录信息
                        localStorage.setItem('token', data.token);
                        localStorage.setItem('userInfo', JSON.stringify(data));

                        this.userInfo = data;
                        this.$message.success('登录成功，欢迎回来！');

                        // 加载用户数据
                        this.loadInitialData();

                        // 清空登录表单
                        this.resetLoginForm();
                    })
                    .catch(error => {
                        console.error('登录失败:', error);
                    })
                    .finally(() => {
                        this.loginLoading = false;
                    });
            });
        },

        // 填充测试账号
        fillTestAccount(type) {
            if (type === 'admin') {
                this.loginForm.username = 'admin';
                this.loginForm.password = 'admin123';
            } else if (type === 'user1') {
                this.loginForm.username = 'user1';
                this.loginForm.password = 'user123';
            }
        },

        // 用户退出
        logout() {
            this.$confirm('确定要退出登录吗？', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                this.performLogout();
            }).catch(() => {
                // 用户取消
            });
        },

        // 执行退出操作
        performLogout() {
            // 清除本地存储
            localStorage.removeItem('token');
            localStorage.removeItem('userInfo');

            // 重置状态
            this.userInfo = null;
            this.evidenceList = [];
            this.recentUploads = [];
            this.fileList = [];
            this.activeTab = 'upload';

            this.$message.success('已退出登录');
        },

        // 处理未授权
        handleUnauthorized() {
            this.$message.error('登录已过期，请重新登录');
            this.performLogout();
        },

        // 重置登录表单
        resetLoginForm() {
            this.loginForm = {
                username: '',
                password: ''
            };
            this.$refs.loginForm && this.$refs.loginForm.resetFields();
        },

        // ==================== 导航相关方法 ====================

        // 处理菜单选择
        handleMenuSelect(key) {
            const tabMap = {
                '1': 'upload',
                '2': 'code',
                '3': 'query',
                '4': 'blockchain'
            };

            const targetTab = tabMap[key] || 'upload';
            console.log('[DEBUG] handleMenuSelect: 切换到标签页', targetTab);

            this.activeTab = targetTab;
            this.activeIndex = key;

            // 注意：不在这里调用loadTabData，让watch监听器处理数据加载
            // 这样避免重复调用API

            // 更新页面标题
            document.title = this.pageTitle;
        },

        // 统一处理标签页数据加载
        loadTabData(tabName) {
            console.log('[DEBUG] loadTabData: 加载标签页数据', tabName);

            switch (tabName) {
                case 'upload':
                    // 文件存证页面数据
                    this.loadEvidenceList();
                    this.loadRecentUploads();
                    this.loadStatistics();
                    break;

                case 'code':
                    // 代码存证页面数据
                    this.loadRepositories();
                    this.loadCodeEvidence();
                    this.loadCodeStats();
                    break;

                case 'query':
                    // 查询验证页面数据
                    this.loadEvidenceList();
                    this.loadStatistics();
                    break;

                case 'blockchain':
                    // 区块链信息页面数据
                    this.refreshBlockchainInfo();
                    this.loadStatistics();
                    break;

                default:
                    console.warn('[DEBUG] loadTabData: 未知的标签页', tabName);
                    break;
            }
        },

        // 显示用户信息
        showUserInfo() {
            this.userInfoVisible = true;
        },

        // ==================== 文件上传相关方法 ====================

        // 上传前检查
        beforeUpload(file) {
            // 检查文件大小
            const isLt100M = file.size / 1024 / 1024 < 100;
            if (!isLt100M) {
                this.$message.error('文件大小不能超过100MB');
                return false;
            }

            // 检查文件类型
            const allowedTypes = [
                'application/pdf',
                'application/msword',
                'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                'application/vnd.ms-excel',
                'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                'text/plain',
                'image/jpeg',
                'image/png',
                'image/gif'
            ];

            if (!allowedTypes.includes(file.type)) {
                this.$message.error('不支持的文件类型');
                return false;
            }

            return true;
        },

        // 提交上传
        submitUpload() {
            if (this.fileList.length === 0) {
                this.$message.warning('请先选择文件');
                return;
            }

            this.uploadLoading = true;
            this.$refs.upload.submit();
        },

        // 上传成功
        handleUploadSuccess(response, file) {
            this.$message.success('文件上传成功！');

            // 清空文件列表和表单
            this.clearFiles();
            this.uploadData.description = '';

            // 刷新数据
            this.loadEvidenceList();
            this.loadRecentUploads();
            this.loadStatistics();

            this.uploadLoading = false;
        },

        // 上传失败
        handleUploadError(error, file) {
            console.error('上传失败:', error);
            this.$message.error('文件上传失败，请重试');
            this.uploadLoading = false;
        },

        // 清空文件
        clearFiles() {
            this.fileList = [];
            this.$refs.upload.clearFiles();
        },

        // 加载最近上传
        loadRecentUploads() {
            if (!this.userInfo) return;

            this.recentLoading = true;
            const params = {
                current: 1,
                size: 5,
                // 按创建时间倒序
            };

            axios.get('/api/evidence/list', { params, showLoading: false })
                .then(data => {
                    this.recentUploads = data.records || [];
                })
                .catch(error => {
                    console.error('加载最近上传失败:', error);
                })
                .finally(() => {
                    this.recentLoading = false;
                });
        },

        // 刷新最近上传
        refreshRecentUploads() {
            this.loadRecentUploads();
        },

        // ==================== 查询相关方法 ====================

        // 执行查询
        handleQuery() {
            this.pagination.current = 1;
            this.loadEvidenceList();
        },

        // 重置查询
        resetQuery() {
            this.queryForm = {
                fileName: '',
                fileHash: '',
                transactionHash: '',
                blockNumber: '',
                chainStatus: null,
                current: 1,
                size: 10
            };
            this.dateRange = null;
            this.pagination.current = 1;
            this.loadEvidenceList();
        },

        // 加载存证列表
        loadEvidenceList() {
            if (!this.userInfo) return;

            this.tableLoading = true;
            const params = {
                ...this.queryForm,
                current: this.pagination.current,
                size: this.pagination.size
            };

            // 清理空值参数
            Object.keys(params).forEach(key => {
                if (params[key] === '' || params[key] === null || params[key] === undefined) {
                    delete params[key];
                }
            });

            axios.get('/api/evidence/list', { params, showLoading: false })
                .then(data => {
                    this.evidenceList = data.records || [];
                    this.pagination.total = data.total || 0;
                    this.pagination.pages = data.pages || 0;
                })
                .catch(error => {
                    console.error('查询存证列表失败:', error);
                    this.evidenceList = [];
                })
                .finally(() => {
                    this.tableLoading = false;
                });
        },

        // 刷新数据
        refreshData() {
            this.loadEvidenceList();
            this.loadStatistics();
        },

        // 导出数据
        exportData() {
            this.$message.info('导出功能开发中...');
            // TODO: 实现数据导出功能
        },

        // 分页大小改变
        handleSizeChange(size) {
            this.pagination.size = size;
            this.pagination.current = 1;
            this.loadEvidenceList();
        },

        // 当前页改变
        handleCurrentChange(current) {
            this.pagination.current = current;
            this.loadEvidenceList();
        },

        // ==================== 存证操作方法 ====================

        // 查看详情
        viewDetail(row) {
            this.selectedEvidence = { ...row };
            this.detailDialogVisible = true;
        },

        // 关闭详情对话框
        handleCloseDetail() {
            this.detailDialogVisible = false;
            this.selectedEvidence = null;
        },

        // 验证存证
        verifyEvidence(fileHash) {
            if (!fileHash) {
                this.$message.warning('文件哈希不能为空');
                return;
            }

            const loading = this.$loading({
                lock: true,
                text: '正在验证存证...',
                spinner: 'el-icon-loading',
                background: 'rgba(0, 0, 0, 0.7)'
            });

            axios.post('/api/evidence/verify', null, {
                params: { fileHash },
                showLoading: false
            }).then(data => {
                if (data) {
                    this.$message.success('存证验证通过！文件确实存在于区块链上');
                } else {
                    this.$message.warning('存证验证失败！文件可能不存在或已被篡改');
                }
            }).catch(error => {
                console.error('验证存证失败:', error);
            }).finally(() => {
                loading.close();
            });
        },

        // 查看上链信息
        viewOnChain(row) {
            if (!row.transactionHash) {
                this.$message.warning('该文件尚未上链');
                return;
            }

            this.$alert(
                `交易哈希: ${row.transactionHash}\n区块号: ${row.blockNumber || '未知'}\n合约地址: ${row.contractAddress || '未知'}`,
                '上链信息',
                {
                    confirmButtonText: '确定',
                    type: 'info',
                    customClass: 'chain-info-alert'
                }
            );
        },

        // 显示快速验证对话框
        showQuickVerify() {
            this.quickVerifyVisible = true;
            this.quickVerifyHash = '';
        },

        // 执行快速验证
        doQuickVerify() {
            if (!this.quickVerifyHash.trim()) {
                this.$message.warning('请输入文件哈希值');
                return;
            }

            this.verifyEvidence(this.quickVerifyHash.trim());
            this.quickVerifyVisible = false;
        },

        // ==================== 区块链信息方法 ====================

        // 刷新区块链信息
        refreshBlockchainInfo() {
            Promise.all([
                this.getBlockNumber(),
                this.getHealthCheck()
            ]).then(() => {
                this.blockchainInfo.connected = true;
            }).catch(() => {
                this.blockchainInfo.connected = false;
            });
        },

        // 获取区块高度
        getBlockNumber() {
            return axios.get('/api/blockchain/block-number', { showLoading: false })
                .then(data => {
                    this.blockchainInfo.blockNumber = data;
                })
                .catch(error => {
                    console.error('获取区块高度失败:', error);
                    this.blockchainInfo.blockNumber = null;
                });
        },

        // 健康检查
        getHealthCheck() {
            return axios.get('/api/health/check', { showLoading: false })
                .then(data => {
                    if (data.blockchain === 'UP') {
                        this.contractAddress = data.contractAddress || '未配置';
                    }
                })
                .catch(error => {
                    console.error('健康检查失败:', error);
                });
        },

        // 加载统计信息
        loadStatistics() {
            if (!this.userInfo) return;

            // 模拟统计数据，实际应该从后端获取
            const today = new Date();
            const todayStr = today.toISOString().split('T')[0];

            axios.get('/api/evidence/list', {
                params: { size: 1000 },
                showLoading: false
            }).then(data => {
                const records = data.records || [];
                this.statistics.totalFiles = records.length;

                // 计算今日上传数量
                const todayFiles = records.filter(record => {
                    const recordDate = new Date(record.createdAt).toISOString().split('T')[0];
                    return recordDate === todayStr;
                });
                this.statistics.todayFiles = todayFiles.length;

                // 计算成功率
                const successFiles = records.filter(record => record.chainStatus === 1);
                if (records.length > 0) {
                    this.statistics.successRate = Math.round((successFiles.length / records.length) * 100) + '%';
                }
            }).catch(error => {
                console.error('加载统计信息失败:', error);
            });
        },

        // 搜索区块链信息
        searchBlockchain() {
            if (!this.searchValue.trim()) {
                this.$message.warning('请输入查询值');
                return;
            }

            const loading = this.$loading({
                lock: true,
                text: '正在查询区块链信息...',
                spinner: 'el-icon-loading',
                background: 'rgba(0, 0, 0, 0.7)'
            });

            let url = '';
            if (this.searchType === 'block') {
                url = `/api/blockchain/block/${this.searchValue}`;
            } else {
                url = `/api/blockchain/transaction/${this.searchValue}`;
            }

            axios.get(url, { showLoading: false })
                .then(data => {
                    this.blockchainSearchResult = data;
                    this.$message.success('查询成功');
                })
                .catch(error => {
                    console.error('查询区块链信息失败:', error);
                    this.blockchainSearchResult = null;
                })
                .finally(() => {
                    loading.close();
                });
        },

        // ==================== 工具方法 ====================

        // 格式化文件大小
        formatFileSize(size) {
            if (!size || size === 0) return '0B';
            const units = ['B', 'KB', 'MB', 'GB', 'TB'];
            let index = 0;
            let fileSize = size;

            while (fileSize >= 1024 && index < units.length - 1) {
                fileSize /= 1024;
                index++;
            }

            return fileSize.toFixed(2) + units[index];
        },

        // 格式化日期
        formatDate(date) {
            if (!date) return '';
            try {
                return new Date(date).toLocaleString('zh-CN', {
                    year: 'numeric',
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit'
                });
            } catch (e) {
                return date;
            }
        },

        // 格式化查询日期
        formatDateForQuery(date) {
            if (!date) return '';
            try {
                return new Date(date).toISOString();
            } catch (e) {
                return '';
            }
        },

        // 获取状态类型
        getStatusType(status) {
            const statusMap = {
                0: 'warning',  // 待上链
                1: 'success',  // 上链成功
                2: 'danger'    // 上链失败
            };
            return statusMap[status] || 'info';
        },

        // 获取状态文本
        getStatusText(status) {
            const statusMap = {
                0: '待上链',
                1: '上链成功',
                2: '上链失败'
            };
            return statusMap[status] || '未知状态';
        },

        // 复制到剪贴板
        copyToClipboard(text) {
            if (!text) return;

            // 使用现代API
            if (navigator.clipboard) {
                navigator.clipboard.writeText(text).then(() => {
                    this.$message.success('已复制到剪贴板');
                }).catch(err => {
                    console.error('复制失败:', err);
                    this.fallbackCopy(text);
                });
            } else {
                this.fallbackCopy(text);
            }
        },

        // 兜底复制方法
        fallbackCopy(text) {
            const textArea = document.createElement('textarea');
            textArea.value = text;
            textArea.style.position = 'fixed';
            textArea.style.opacity = '0';
            document.body.appendChild(textArea);
            textArea.focus();
            textArea.select();

            try {
                document.execCommand('copy');
                this.$message.success('已复制到剪贴板');
            } catch (err) {
                console.error('复制失败:', err);
                this.$message.error('复制失败，请手动复制');
            }

            document.body.removeChild(textArea);
        },

        // 显示全局加载
        showGlobalLoading() {
            // 可以在这里实现全局加载效果
        },

        // 隐藏全局加载
        hideGlobalLoading() {
            // 可以在这里隐藏全局加载效果
        },

        // ==================== 事件处理方法 ====================

        // 处理浏览器前进后退
        handlePopState(event) {
            // 处理路由变化
            console.log('路由变化:', event);
        },

        // 处理网络连接
        handleOnline() {
            this.$message.success('网络连接已恢复');
            this.refreshBlockchainInfo();
        },

        // 处理网络断开
        handleOffline() {
            this.$message.warning('网络连接已断开');
        },

        // ==================== 其他方法 ====================

        // 获取当前时间戳
        getCurrentTimestamp() {
            return Date.now();
        },

        // 生成随机ID
        generateRandomId() {
            return Math.random().toString(36).substr(2, 9);
        },

        // 防抖函数
        debounce(func, wait) {
            let timeout;
            return function executedFunction(...args) {
                const later = () => {
                    clearTimeout(timeout);
                    func(...args);
                };
                clearTimeout(timeout);
                timeout = setTimeout(later, wait);
            };
        },

        // 节流函数
        throttle(func, limit) {
            let inThrottle;
            return function() {
                const args = arguments;
                const context = this;
                if (!inThrottle) {
                    func.apply(context, args);
                    inThrottle = true;
                    setTimeout(() => inThrottle = false, limit);
                }
            };
        },

        // ==================== 代码存证方法 ====================

        // 创建仓库
        async createRepository() {
            // 表单验证
            const isValid = await new Promise((resolve) => {
                this.$refs.repositoryForm.validate((valid) => {
                    resolve(valid);
                });
            });
            
            if (!isValid) return;
            
            // 检查仓库是否已存在
            const exists = this.repositories.find(repo => 
                repo.groupName === this.repositoryForm.groupName && 
                repo.projectName === this.repositoryForm.projectName
            );
            if (exists) {
                this.$message.error('仓库已存在');
                return;
            }
            
            this.codeLoading.createRepo = true;
            try {
                const response = await this.apiRequest('POST', '/api/code/repository', this.repositoryForm);
                if (response.code === 200) {
                    this.$message.success('仓库创建成功');
                    this.repositoryForm = { groupName: '', projectName: '', description: '' };
                    this.loadRepositories();
                    this.loadCodeStats();
                } else {
                    this.$message.error(response.message || '创建失败');
                }
            } catch (error) {
                console.error('创建仓库失败:', error);
                this.$message.error('创建失败：' + error.message);
            } finally {
                this.codeLoading.createRepo = false;
            }
        },

        // 创建分支
        async createBranch() {
            if (!this.selectedRepository || !this.branchForm.branchName) {
                this.$message.error('请选择仓库并填写分支名称');
                return;
            }
            
            // 验证分支名称格式
            const branchRegex = /^[a-zA-Z0-9_/-]+$/;
            if (!branchRegex.test(this.branchForm.branchName)) {
                this.$message.error('分支名称只能包含字母、数字、下划线、连字符和斜杠');
                return;
            }
            
            // 检查分支是否已存在
            if (this.branches.find(b => b.branchName === this.branchForm.branchName)) {
                this.$message.error('分支名称已存在');
                return;
            }
            
            this.codeLoading.createBranch = true;
            try {
                const params = {
                    repositoryId: this.selectedRepository,
                    branchName: this.branchForm.branchName,
                    baseBranch: this.branchForm.baseBranch || 'main'
                };
                
                const response = await this.apiRequest('POST', '/api/code/branch', params);
                if (response.code === 200) {
                    this.$message.success('分支创建成功');
                    this.branchForm = { branchName: '', baseBranch: '' };
                    this.loadBranches();
                    this.loadCodeStats();
                } else {
                    this.$message.error(response.message || '创建失败');
                }
            } catch (error) {
                console.error('创建分支失败:', error);
                this.$message.error('创建失败：' + error.message);
            } finally {
                this.codeLoading.createBranch = false;
            }
        },

        // 上传代码
        async uploadCode() {
            if (!this.codeUploadForm.repositoryId || !this.codeUploadForm.branchName || this.codeFileList.length === 0) {
                this.$message.error('请选择仓库、分支并上传文件');
                return;
            }
            
            // 验证文件类型和大小
            const validExtensions = ['.py', '.java', '.js', '.ts', '.html', '.css', '.sql', '.json', '.xml', '.c', '.cpp', '.h', '.hpp', '.go', '.rs', '.php', '.rb', '.swift', '.kt', '.scala', '.sol', '.vue', '.jsx', '.tsx'];
            const invalidFiles = this.codeFileList.filter(file => {
                const ext = '.' + file.name.split('.').pop().toLowerCase();
                return !validExtensions.includes(ext);
            });
            
            if (invalidFiles.length > 0) {
                this.$message.error(`不支持的文件类型: ${invalidFiles.map(f => f.name).join(', ')}`);
                return;
            }
            
            const maxSize = 10 * 1024 * 1024; // 10MB
            const oversizedFiles = this.codeFileList.filter(file => file.size > maxSize);
            if (oversizedFiles.length > 0) {
                this.$message.error(`文件过大(超过10MB): ${oversizedFiles.map(f => f.name).join(', ')}`);
                return;
            }
            
            this.codeLoading.upload = true;
            let successCount = 0;
            let failCount = 0;
            
            try {
                for (let fileItem of this.codeFileList) {
                    try {
                        const formData = new FormData();
                        formData.append('repositoryId', this.codeUploadForm.repositoryId);
                        formData.append('branchName', this.codeUploadForm.branchName);
                        formData.append('file', fileItem.raw);
                        formData.append('fileName', fileItem.name);
                        formData.append('commitMessage', this.codeUploadForm.commitMessage || `上传文件: ${fileItem.name}`);
                        
                        const response = await this.apiRequestFormData('POST', '/api/code/commit', formData);
                        
                        if (response.code === 200) {
                            successCount++;
                        } else {
                            failCount++;
                            console.error(`上传 ${fileItem.name} 失败:`, response.message);
                        }
                    } catch (error) {
                        failCount++;
                        console.error(`上传 ${fileItem.name} 异常:`, error);
                    }
                }
                
                // 显示上传结果
                if (successCount > 0 && failCount === 0) {
                    this.$message.success(`成功上传 ${successCount} 个文件`);
                } else if (successCount > 0 && failCount > 0) {
                    this.$message.warning(`成功上传 ${successCount} 个文件，${failCount} 个文件上传失败`);
                } else {
                    this.$message.error(`所有文件上传失败`);
                }
                
                if (successCount > 0) {
                    this.clearCodeFiles();
                    this.codeUploadForm.commitMessage = '';
                    this.loadCodeEvidence();
                    this.loadCodeStats();
                }
            } catch (error) {
                console.error('上传代码失败:', error);
                this.$message.error('上传失败：' + error.message);
            } finally {
                this.codeLoading.upload = false;
            }
        },

        // 加载仓库列表
        async loadRepositories() {
            try {
                console.log('开始加载仓库列表...');
                const response = await this.apiRequest('GET', '/api/code/repositories');
                console.log('仓库列表API响应:', response);
                if (response.code === 200) {
                    this.repositories = response.data || [];
                    console.log('设置repositories数据:', this.repositories);
                    console.log('repositories数组长度:', this.repositories.length);
                } else {
                    console.error('API返回错误:', response);
                }
            } catch (error) {
                console.error('加载仓库列表失败:', error);
            }
        },

        // 加载分支列表
        async loadBranches() {
            if (!this.selectedRepository) {
                this.branches = [];
                return;
            }
            
            try {
                const response = await this.apiRequest('GET', `/api/code/branches?repositoryId=${this.selectedRepository}`);
                if (response.code === 200) {
                    this.branches = response.data || [];
                    // 如果没有选中分支且有分支列表，默认选中main分支
                    if (!this.codeUploadForm.branchName && this.branches.length > 0) {
                        const mainBranch = this.branches.find(b => b.branchName === 'main');
                        this.codeUploadForm.branchName = mainBranch ? 'main' : this.branches[0].branchName;
                    }
                }
            } catch (error) {
                console.error('加载分支列表失败:', error);
            }
        },

        // 加载代码存证记录
        async loadCodeEvidence() {
            this.codeLoading.table = true;
            try {
                const response = await this.apiRequest('GET', '/api/code/evidence');
                if (response.code === 200) {
                    this.codeEvidenceList = response.data || [];
                } else {
                    this.$message.error('加载存证记录失败：' + response.message);
                }
            } catch (error) {
                console.error('加载存证记录失败:', error);
                this.$message.error('加载存证记录失败，请检查网络连接');
            } finally {
                this.codeLoading.table = false;
            }
        },

        // 加载代码统计信息
        loadCodeStats() {
            this.codeStats = {
                totalRepositories: this.repositories.length,
                totalBranches: this.branches.length,
                totalCommits: this.codeEvidenceList.length,
                todayCommits: this.codeEvidenceList.filter(item => {
                    const today = new Date().toDateString();
                    const itemDate = new Date(item.createdAt).toDateString();
                    return today === itemDate;
                }).length
            };
        },

        // 仓库变更
        onRepositoryChange() {
            this.codeUploadForm.repositoryId = this.selectedRepository;
            this.loadBranches();
        },

        // 代码文件选择
        handleCodeFileSelect(file, fileList) {
            this.codeFileList = fileList;
        },

        // 清空代码文件
        clearCodeFiles() {
            this.codeFileList = [];
            this.$refs.codeUpload.clearFiles();
        },

        // 查看代码详情
        viewCodeDetails(row) {
            this.$alert(`
                <div style="text-align: left;">
                    <p><strong>文件名：</strong>${row.fileName}</p>
                    <p><strong>文件哈希：</strong>${row.fileHash}</p>
                    <p><strong>仓库：</strong>${row.gitGroupName}/${row.gitProjectName}</p>
                    <p><strong>分支：</strong>${row.gitBranchName}</p>
                    <p><strong>提交哈希：</strong>${row.gitCommitHash || '-'}</p>
                    <p><strong>提交信息：</strong>${row.gitCommitMessage || '-'}</p>
                    <p><strong>作者：</strong>${row.gitAuthorName || '-'}</p>
                    <p><strong>存证时间：</strong>${row.createdAt}</p>
                    <p><strong>区块号：</strong>${row.blockNumber || '-'}</p>
                    <p><strong>交易哈希：</strong>${row.transactionHash || '-'}</p>
                </div>
            `, '存证详情', {
                dangerouslyUseHTMLString: true,
                customClass: 'evidence-detail-dialog'
            });
        },

        // 下载代码
        downloadCode(row) {
            this.$message.info('下载功能开发中...');
        },

        // 获取链上状态类型
        getChainStatusType(status) {
            switch (status) {
                case 0: return 'warning'; // 待上链
                case 1: return 'success'; // 已上链
                case 2: return 'danger';  // 失败
                default: return 'info';
            }
        },

        // 获取链上状态文本
        getChainStatusText(status) {
            switch (status) {
                case 0: return '待上链';
                case 1: return '已上链';
                case 2: return '失败';
                default: return '未知';
            }
        },

        // API请求封装（FormData）
        async apiRequestFormData(method, url, formData) {
            try {
                const response = await axios({
                    method: method,
                    url: url,
                    data: formData,
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                });
                return response.data;
            } catch (error) {
                if (error.response) {
                    throw new Error(`HTTP ${error.response.status}: ${error.response.statusText}`);
                } else {
                    throw error;
                }
            }
        },

        // ==================== 文件存证管理方法 ====================

        // 处理文件存证视图变化
        handleFileEvidenceViewChange(view) {
            this.fileEvidenceActiveView = view;
            if (view === 'groups') {
                this.loadUserGroups();
            } else if (view === 'projects') {
                this.loadUserProjects();
            }
        },

        // 处理分组变化
        onGroupChange(groupId) {
            this.selectedGroupId = groupId;
            this.selectedProjectId = null;
            this.loadProjectsByGroup(groupId);
        },

        // 加载用户分组
        loadUserGroups() {
            axios.get('/api/file-evidence/groups')
                .then(response => {
                    this.userGroups = response.data || [];
                })
                .catch(error => {
                    console.error('加载用户分组失败:', error);
                    this.userGroups = [];
                });
        },

        // 加载用户项目
        loadUserProjects() {
            axios.get('/api/file-evidence/projects')
                .then(response => {
                    this.currentProjects = response.data || [];
                })
                .catch(error => {
                    console.error('加载用户项目失败:', error);
                    this.currentProjects = [];
                });
        },

        // 根据分组ID加载项目
        loadProjectsByGroup(groupId) {
            if (!groupId) {
                this.currentProjects = [];
                return;
            }
            
            axios.get(`/api/file-evidence/projects/by-group/${groupId}`)
                .then(response => {
                    this.currentProjects = response.data || [];
                })
                .catch(error => {
                    console.error('加载分组项目失败:', error);
                    this.currentProjects = [];
                });
        }
    }
});

// 全局错误处理
window.addEventListener('error', function(event) {
    console.error('全局错误:', event.error);
});

// 全局未处理的Promise拒绝
window.addEventListener('unhandledrejection', function(event) {
    console.error('未处理的Promise拒绝:', event.reason);
});

// 页面性能监控
window.addEventListener('load', function() {
    if (window.performance) {
        const perfData = window.performance.timing;
        const loadTime = perfData.loadEventEnd - perfData.navigationStart;
        console.log('页面加载时间:', loadTime + 'ms');
    }
});

// 服务工作者注册（PWA支持）
if ('serviceWorker' in navigator) {
    window.addEventListener('load', function() {
        navigator.serviceWorker.register('/sw.js')
            .then(function(registration) {
                console.log('ServiceWorker注册成功:', registration.scope);
            })
            .catch(function(err) {
                console.log('ServiceWorker注册失败:', err);
            });
    });
}