const I18n = {
    // 当前语言
    currentLang: 'zh-CN',

    // 语言包
    messages: {
        'zh-CN': {
            // 通用
            'common.confirm': '确定',
            'common.cancel': '取消',
            'common.save': '保存',
            'common.delete': '删除',
            'common.edit': '编辑',
            'common.view': '查看',
            'common.search': '搜索',
            'common.reset': '重置',
            'common.refresh': '刷新',
            'common.loading': '加载中...',
            'common.success': '操作成功',
            'common.error': '操作失败',
            'common.warning': '警告',
            'common.info': '提示',

            // 登录
            'login.title': '用户登录',
            'login.username': '用户名',
            'login.password': '密码',
            'login.remember': '记住登录状态',
            'login.submit': '立即登录',
            'login.username.required': '请输入用户名',
            'login.password.required': '请输入密码',
            'login.success': '登录成功',
            'login.failed': '登录失败',

            // 导航
            'nav.upload': '文件存证',
            'nav.query': '查询验证',
            'nav.blockchain': '区块链信息',
            'nav.logout': '退出登录',

            // 上传
            'upload.title': '文件上传存证',
            'upload.selectFile': '选择文件',
            'upload.dragTip': '将文件拖到此处，或点击上传',
            'upload.description': '文件描述',
            'upload.hashAlgorithm': '哈希算法',
            'upload.start': '开始上传存证',
            'upload.clear': '清空文件',
            'upload.success': '文件上传成功',
            'upload.failed': '文件上传失败',
            'upload.sizeLimit': '文件大小不能超过100MB',
            'upload.typeNotSupported': '不支持的文件类型',

            // 查询
            'query.title': '存证查询验证',
            'query.fileName': '文件名',
            'query.fileHash': '文件哈希',
            'query.transactionHash': '交易哈希',
            'query.blockNumber': '区块号',
            'query.status': '状态',
            'query.timeRange': '时间范围',
            'query.detail': '详情',
            'query.verify': '验证',
            'query.quickVerify': '快速验证',
            'query.export': '导出',

            // 状态
            'status.pending': '待上链',
            'status.success': '上链成功',
            'status.failed': '上链失败',

            // 验证
            'verify.success': '验证通过！文件确实存在于区块链上',
            'verify.failed': '验证失败！文件可能不存在或已被篡改',
            'verify.enterHash': '请输入文件哈希值',

            // 区块链信息
            'blockchain.title': '区块链信息',
            'blockchain.status': '区块链状态',
            'blockchain.blockHeight': '当前块高',
            'blockchain.connected': '已连接',
            'blockchain.disconnected': '未连接',
            'blockchain.groupId': '群组ID',
            'blockchain.contractAddress': '合约地址',
            'blockchain.statistics': '统计信息',
            'blockchain.totalFiles': '总文件数',
            'blockchain.todayFiles': '今日上传',
            'blockchain.successRate': '成功率',

            // 错误信息
            'error.network': '网络错误，请检查连接',
            'error.timeout': '请求超时，请重试',
            'error.unauthorized': '未授权访问',
            'error.forbidden': '没有权限访问该资源',
            'error.notFound': '资源不存在',
            'error.serverError': '服务器内部错误',
            'error.unknown': '未知错误'
        },

        'en-US': {
            // Common
            'common.confirm': 'Confirm',
            'common.cancel': 'Cancel',
            'common.save': 'Save',
            'common.delete': 'Delete',
            'common.edit': 'Edit',
            'common.view': 'View',
            'common.search': 'Search',
            'common.reset': 'Reset',
            'common.refresh': 'Refresh',
            'common.loading': 'Loading...',
            'common.success': 'Operation successful',
            'common.error': 'Operation failed',
            'common.warning': 'Warning',
            'common.info': 'Info',

            // Login
            'login.title': 'User Login',
            'login.username': 'Username',
            'login.password': 'Password',
            'login.remember': 'Remember me',
            'login.submit': 'Login',
            'login.username.required': 'Please enter username',
            'login.password.required': 'Please enter password',
            'login.success': 'Login successful',
            'login.failed': 'Login failed',

            // Navigation
            'nav.upload': 'File Evidence',
            'nav.query': 'Query & Verify',
            'nav.blockchain': 'Blockchain Info',
            'nav.logout': 'Logout',

            // Upload
            'upload.title': 'File Upload Evidence',
            'upload.selectFile': 'Select File',
            'upload.dragTip': 'Drag files here, or click to upload',
            'upload.description': 'File Description',
            'upload.hashAlgorithm': 'Hash Algorithm',
            'upload.start': 'Start Upload',
            'upload.clear': 'Clear File',
            'upload.success': 'File uploaded successfully',
            'upload.failed': 'File upload failed',
            'upload.sizeLimit': 'File size cannot exceed 100MB',
            'upload.typeNotSupported': 'Unsupported file type',

            // Query
            'query.title': 'Evidence Query & Verification',
            'query.fileName': 'File Name',
            'query.fileHash': 'File Hash',
            'query.transactionHash': 'Transaction Hash',
            'query.blockNumber': 'Block Number',
            'query.status': 'Status',
            'query.timeRange': 'Time Range',
            'query.detail': 'Detail',
            'query.verify': 'Verify',
            'query.quickVerify': 'Quick Verify',
            'query.export': 'Export',

            // Status
            'status.pending': 'Pending',
            'status.success': 'Success',
            'status.failed': 'Failed',

            // Verification
            'verify.success': 'Verification passed! File exists on blockchain',
            'verify.failed': 'Verification failed! File may not exist or has been tampered',
            'verify.enterHash': 'Please enter file hash',

            // Blockchain Info
            'blockchain.title': 'Blockchain Information',
            'blockchain.status': 'Blockchain Status',
            'blockchain.blockHeight': 'Current Block Height',
            'blockchain.connected': 'Connected',
            'blockchain.disconnected': 'Disconnected',
            'blockchain.groupId': 'Group ID',
            'blockchain.contractAddress': 'Contract Address',
            'blockchain.statistics': 'Statistics',
            'blockchain.totalFiles': 'Total Files',
            'blockchain.todayFiles': 'Today\'s Upload',
            'blockchain.successRate': 'Success Rate',

            // Error Messages
            'error.network': 'Network error, please check connection',
            'error.timeout': 'Request timeout, please retry',
            'error.unauthorized': 'Unauthorized access',
            'error.forbidden': 'No permission to access this resource',
            'error.notFound': 'Resource not found',
            'error.serverError': 'Internal server error',
            'error.unknown': 'Unknown error'
        }
    },

    // 获取翻译文本
    t(key, params = {}) {
        const keys = key.split('.');
        let value = this.messages[this.currentLang];

        for (const k of keys) {
            if (value && typeof value === 'object') {
                value = value[k];
            } else {
                return key; // 返回原始key如果找不到翻译
            }
        }

        if (typeof value === 'string') {
            // 替换参数
            return value.replace(/\{(\w+)\}/g, (match, param) => {
                return params[param] || match;
            });
        }

        return key;
    },

    // 设置语言
    setLang(lang) {
        if (this.messages[lang]) {
            this.currentLang = lang;
            localStorage.setItem('language', lang);

            // 触发语言变化事件
            window.dispatchEvent(new CustomEvent('languageChanged', {
                detail: { language: lang }
            }));
        }
    },

    // 获取当前语言
    getLang() {
        return this.currentLang;
    },

    // 获取支持的语言列表
    getSupportedLangs() {
        return Object.keys(this.messages);
    },

    // 初始化
    init() {
        // 从本地存储读取语言设置
        const savedLang = localStorage.getItem('language');
        if (savedLang && this.messages[savedLang]) {
            this.currentLang = savedLang;
        } else {
            // 自动检测浏览器语言
            const browserLang = navigator.language || navigator.userLanguage;
            if (browserLang.startsWith('zh')) {
                this.currentLang = 'zh-CN';
            } else {
                this.currentLang = 'en-US';
            }
        }
    }
};

// 初始化
I18n.init();

// 挂载到全局
window.I18n = I18n;
window.$t = I18n.t.bind(I18n);