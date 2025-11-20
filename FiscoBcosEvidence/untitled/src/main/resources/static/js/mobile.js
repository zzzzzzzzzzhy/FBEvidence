new Vue({
    el: '#mobileApp',
    data() {
        return {
            // 导航
            activeTab: 'upload',
            pageTitle: '区块链存证',

            // 用户信息
            userInfo: null,

            // 上传相关
            fileList: [],
            uploadData: {
                description: '',
                hashAlgorithm: 'SHA256'
            },
            uploadLoading: false,
            showHashPicker: false,
            hashOptions: ['SHA256', 'SM3'],

            // 查询相关
            searchKeyword: '',
            evidenceList: [],
            loading: false,
            finished: false,
            page: 1,
            pageSize: 10,

            // 详情
            showDetail: false,
            selectedEvidence: null,

            // 区块链信息
            blockchainInfo: {
                blockNumber: null,
                connected: false,
                groupId: 'group0'
            },

            // 统计信息
            statistics: {
                totalFiles: 0,
                todayFiles: 0,
                successRate: '0%'
            }
        }
    },

    watch: {
        activeTab(newVal) {
            const titles = {
                'upload': '文件上传',
                'query': '存证查询',
                'info': '系统信息'
            };
            this.pageTitle = titles[newVal] || '区块链存证';
        }
    },

    created() {
        this.initializeApp();
    },

    methods: {
        initializeApp() {
            this.checkLogin();
            this.setupAxios();
            this.loadInitialData();
        },

        checkLogin() {
            const token = localStorage.getItem('token');
            const userInfo = localStorage.getItem('userInfo');

            if (!token || !userInfo) {
                this.$toast('请先登录系统');
                window.location.href = '/';
                return;
            }

            try {
                this.userInfo = JSON.parse(userInfo);
            } catch (e) {
                this.$toast('用户信息解析失败');
                this.logout();
            }
        },

        setupAxios() {
            const token = localStorage.getItem('token');
            if (token) {
                axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
            }

            // 响应拦截器
            axios.interceptors.response.use(
                response => response.data,
                error => {
                    if (error.response?.status === 401) {
                        this.$toast('登录已过期');
                        this.logout();
                    } else {
                        this.$toast(error.response?.data?.message || '网络错误');
                    }
                    return Promise.reject(error);
                }
            );
        },

        loadInitialData() {
            this.loadBlockchainInfo();
            this.loadStatistics();
            if (this.activeTab === 'query') {
                this.onLoad();
            }
        },

        // 导航方法
        onClickLeft() {
            // 处理返回操作
        },

        showUserInfo() {
            if (this.userInfo) {
                this.$dialog.alert({
                    title: '用户信息',
                    message: `用户名：${this.userInfo.username}\n真实姓名：${this.userInfo.realName || '未设置'}\n邮箱：${this.userInfo.email || '未设置'}`
                });
            }
        },

        // 上传相关方法
        afterRead(file) {
            // 验证文件
            const maxSize = 100 * 1024 * 1024; // 100MB
            if (file.file.size > maxSize) {
                this.$toast('文件大小不能超过100MB');
                this.fileList = [];
                return;
            }

            const allowedTypes = [
                'application/pdf',
                'application/msword',
                'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                'text/plain',
                'image/jpeg',
                'image/png'
            ];

            if (!allowedTypes.includes(file.file.type)) {
                this.$toast('不支持的文件类型');
                this.fileList = [];
                return;
            }
        },

        beforeUpload() {
            return new Promise((resolve) => {
                // 这里可以添加上传前的处理逻辑
                resolve();
            });
        },

        async submitUpload() {
            if (this.fileList.length === 0) {
                this.$toast('请先选择文件');
                return;
            }

            const formData = new FormData();
            formData.append('file', this.fileList[0].file);

            if (this.uploadData.description) {
                formData.append('description', this.uploadData.description);
            }

            formData.append('hashAlgorithm', this.uploadData.hashAlgorithm);

            this.uploadLoading = true;

            try {
                const response = await axios.post('/api/evidence/upload', formData);
                this.$toast.success('上传成功');

                // 清空表单
                this.fileList = [];
                this.uploadData.description = '';

                // 刷新统计信息
                this.loadStatistics();

            } catch (error) {
                console.error('上传失败:', error);
            } finally {
                this.uploadLoading = false;
            }
        },

        onHashConfirm(value) {
            this.uploadData.hashAlgorithm = value;
            this.showHashPicker = false;
        },

        // 查询相关方法
        onSearch() {
            this.evidenceList = [];
            this.page = 1;
            this.finished = false;
            this.onLoad();
        },

        async onLoad() {
            if (this.loading || this.finished) return;

            this.loading = true;

            try {
                const params = {
                    current: this.page,
                    size: this.pageSize
                };

                if (this.searchKeyword) {
                    params.fileName = this.searchKeyword;
                }

                const response = await axios.get('/api/evidence/list', { params });
                const records = response.data.records || [];

                if (records.length === 0) {
                    this.finished = true;
                } else {
                    this.evidenceList.push(...records);
                    this.page++;
                }

            } catch (error) {
                console.error('加载数据失败:', error);
            } finally {
                this.loading = false;
            }
        },

        viewDetail(item) {
            this.selectedEvidence = item;
            this.showDetail = true;
        },

        async verifyEvidence(fileHash) {
            try {
                const response = await axios.post('/api/evidence/verify', null, {
                    params: { fileHash }
                });

                if (response.data) {
                    this.$toast.success('验证通过！文件存在于区块链上');
                } else {
                    this.$toast.fail('验证失败！文件可能不存在或已被篡改');
                }
            } catch (error) {
                console.error('验证失败:', error);
            }
        },

        // 信息页面方法
        async loadBlockchainInfo() {
            try {
                const response = await axios.get('/api/health/check');
                this.blockchainInfo.connected = response.data.blockchain === 'UP';
                this.blockchainInfo.blockNumber = response.data.blockNumber;
            } catch (error) {
                this.blockchainInfo.connected = false;
                console.error('加载区块链信息失败:', error);
            }
        },

        async loadStatistics() {
            try {
                // 模拟加载统计信息
                const response = await axios.get('/api/evidence/list', {
                    params: { size: 1000 }
                });

                const records = response.data.records || [];
                this.statistics.totalFiles = records.length;

                // 计算今日上传
                const today = new Date().toISOString().split('T')[0];
                const todayFiles = records.filter(record => {
                    const recordDate = new Date(record.createdAt).toISOString().split('T')[0];
                    return recordDate === today;
                });
                this.statistics.todayFiles = todayFiles.length;

                // 计算成功率
                const successFiles = records.filter(record => record.chainStatus === 1);
                if (records.length > 0) {
                    this.statistics.successRate = Math.round((successFiles.length / records.length) * 100) + '%';
                }
            } catch (error) {
                console.error('加载统计信息失败:', error);
            }
        },

        logout() {
            this.$dialog.confirm({
                title: '退出登录',
                message: '确定要退出登录吗？'
            }).then(() => {
                localStorage.removeItem('token');
                localStorage.removeItem('userInfo');
                this.$toast('已退出登录');
                window.location.href = '/';
            }).catch(() => {
                // 用户取消
            });
        },

        // 工具方法
        formatFileSize(bytes) {
            if (!bytes) return '0B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + sizes[i];
        },

        formatDate(dateString) {
            if (!dateString) return '';
            return new Date(dateString).toLocaleString('zh-CN');
        },

        getStatusType(status) {
            const statusMap = {
                0: 'warning',
                1: 'success',
                2: 'danger'
            };
            return statusMap[status] || 'default';
        },

        getStatusText(status) {
            const statusMap = {
                0: '待上链',
                1: '上链成功',
                2: '上链失败'
            };
            return statusMap[status] || '未知状态';
        }
    }
});