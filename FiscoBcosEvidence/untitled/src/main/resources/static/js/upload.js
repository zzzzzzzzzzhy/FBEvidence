new Vue({
    el: '#uploadApp',

    data() {
        return {
            // 接口
            uploadAction: '/api/evidence/upload',
            uploadHeaders: {},

            // 与 UploadRequest 字段名完全一致
            uploadData: {
                description: '',
                hashAlgorithm: 'SHA256'
            },

            // Element-UI 受控文件列表
            fileList: [],

            // 进度/状态
            uploadLoading: false,
            uploadProgress: 0,
            uploadStatus: '',
            progressText: '准备上传...',

            // 文字内容存证
            textContent: '',

            // 上传历史（可选）
            uploadHistory: [],
            historyLoading: false
        };
    },

    computed: {
        // 是否已有可上传的文件（双保险：看受控列表 or 组件内部列表）
        hasFile() {
            const inner = this.$refs.uploader ? this.$refs.uploader.uploadFiles : [];
            return this.fileList.length > 0 || (inner && inner.length > 0);
        },
        canUpload() {
            // 支持文件上传或文字内容存证
            const hasContent = this.hasFile || (this.textContent && this.textContent.trim().length > 0);
            return hasContent && !this.uploadLoading;
        },
        // 检查是否有文字内容
        hasTextContent() {
            return this.textContent && this.textContent.trim().length > 0;
        },
        // 方便模板里直接取“当前文件”
        currentFile() {
            if (this.fileList.length > 0) return this.fileList[0];
            const inner = this.$refs.uploader ? this.$refs.uploader.uploadFiles : [];
            return inner && inner[0] ? inner[0] : {};
        }
    },

    created() {
        console.log('Vue app created');
        // 简单鉴权检查
        const token = localStorage.getItem('token');
        if (!token) {
            this.$message.error('请先登录系统');
            window.location.href = '/';
            return;
        }
        // el-upload 的 XHR 不继承 axios 的默认头，需单独设置
        this.uploadHeaders = { Authorization: `Bearer ${token}` };
        // axios 用于历史/验证等接口
        axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;

        // 拉取近期记录（可选）
        this.loadUploadHistory();
    },

    methods: {
        // —— el-upload 受控模式必须同步 fileList
        onFileChange(file, fileList) {
            console.log('fileList:', fileList);
            this.fileList = fileList;
        },
        onFileRemove(file, fileList) {
            this.fileList = fileList;
        },

        // 进度
        onUploadProgress(evt /*, file, fileList */) {
            this.uploadLoading = true;
            this.uploadProgress = Math.round(evt.percent || 0);
            this.uploadStatus = '';
            this.progressText = `已上传 ${this.uploadProgress}%`;
        },

        // 上传前校验（大小 + 类型，类型为空时放行，用扩展名兜底）
        beforeUpload(file) {
            console.log('beforeUpload triggered:', file);
            console.log('file name:', file.name);
            console.log('file size:', file.size, 'bytes (', (file.size / 1024 / 1024).toFixed(2), 'MB)');
            console.log('file type:', file.type);
            
            const isLt100M = file.size / 1024 / 1024 < 100;
            if (!isLt100M) {
                console.log('文件大小超限，被拒绝');
                this.$message.error('文件大小不能超过 100MB');
                return false;
            }

            const allowedMime = new Set([
                'application/pdf',
                'application/msword',
                'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                'application/vnd.ms-excel',
                'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                'text/plain',
                'image/jpeg',
                'image/jpg',
                'image/png',
                'image/gif'
            ]);
            const allowedExt = new Set(['pdf','doc','docx','xls','xlsx','txt','jpg','jpeg','png','gif']);
            const typeOk = file.type ? allowedMime.has(file.type) : true;
            const ext = (file.name.split('.').pop() || '').toLowerCase();
            const extOk = allowedExt.has(ext);

            console.log('type check - typeOk:', typeOk, 'extOk:', extOk);
            console.log('file extension:', ext);
            
            if (!typeOk && !extOk) {
                console.log('文件类型不支持，被拒绝');
                this.$message.error('不支持的文件类型');
                return false;
            }
            
            console.log('文件验证通过');
            return true;
        },

        // 点击"开始上传"
        startUpload() {
            if (!this.canUpload) {
                this.$message.warning('请先选择文件或输入文字内容');
                return;
            }

            this.uploadLoading = true;
            this.uploadProgress = 0;
            this.uploadStatus = '';

            // 判断是文件上传还是文字内容上传
            if (this.hasFile) {
                // 文件上传
                this.progressText = '开始上传文件...';
                this.$refs.uploader.submit();
            } else if (this.hasTextContent) {
                // 文字内容上传
                this.progressText = '开始上传文字内容...';
                this.submitTextContent();
            }
        },

        // 提交文字内容
        submitTextContent() {
            const formData = new FormData();
            
            // 将文字内容作为Blob添加到formData中
            const textBlob = new Blob([this.textContent], { type: 'text/plain' });
            const fileName = `text_content_${Date.now()}.txt`;
            formData.append('file', textBlob, fileName);
            
            // 添加其他参数
            formData.append('description', this.uploadData.description || '文字内容存证');
            formData.append('hashAlgorithm', this.uploadData.hashAlgorithm);
            formData.append('contentType', 'TEXT'); // 标识这是文字内容

            // 使用axios直接上传
            axios.post(this.uploadAction, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                    ...this.uploadHeaders
                },
                onUploadProgress: (progressEvent) => {
                    this.uploadProgress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                    this.progressText = `文字内容上传中 ${this.uploadProgress}%`;
                }
            })
            .then(response => {
                this.onSuccess(response.data);
            })
            .catch(error => {
                this.onError(error);
            });
        },

        // 成功回调（兼容常见 Result 包装）
        onSuccess(res /*, file, list */) {
            this.uploadProgress = 100;
            this.uploadStatus = 'success';
            this.progressText = '上传成功！';
            const msg = (res && (res.message || res.msg)) || '文件上传成功';
            this.$message.success(msg);

            setTimeout(() => {
                this.uploadLoading = false;
                this.clearFiles();
                this.clearTextContent();
                this.uploadData.description = '';
                this.loadUploadHistory();
            }, 600);
        },

        // 失败回调
        onError(err /*, file, list */) {
            this.uploadStatus = 'exception';
            this.progressText = '上传失败！';
            this.uploadLoading = false;
            const msg = (err && err.message) ? err.message : '文件上传失败，请重试';
            this.$message.error(msg);
        },

        // 清空文件
        clearFiles() {
            if (this.$refs.uploader) this.$refs.uploader.clearFiles();
            this.fileList = [];
        },

        // 清空文字内容
        clearTextContent() {
            this.textContent = '';
        },

        // —— 历史记录（可选）
        loadUploadHistory() {
            this.historyLoading = true;
            axios.get('/api/evidence/list', { params: { current: 1, size: 10 } })
                .then(res => {
                    const page = res || {};
                    this.uploadHistory = page.records || page.list || [];
                })
                .catch(() => { this.uploadHistory = []; })
                .finally(() => { this.historyLoading = false; });
        },
        refreshHistory() { this.loadUploadHistory(); },

        // 其它动作（可选）
        viewDetails(row) { window.open(`/?tab=query&id=${row.id}`, '_blank'); },
        verifyFile(fileHash) {
            axios.post('/api/evidence/verify', null, { params: { fileHash } })
                .then(res => {
                    const ok = !!(res && (res === true || res.success === true));
                    ok ? this.$message.success('文件验证通过！') : this.$message.warning('文件验证失败！');
                });
        },
        goBack() { window.location.href = '/'; },

        // 工具
        formatFileSize(bytes) {
            if (!bytes && bytes !== 0) return '';
            const k = 1024, sizes = ['B','KB','MB','GB'];
            const i = Math.floor(Math.log(bytes || 1) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        },
        formatDate(ts) {
            if (!ts) return '';
            return new Date(ts).toLocaleString('zh-CN');
        },
        getStatusType(s) { return {0:'warning',1:'success',2:'danger'}[s] || 'info'; },
        getStatusText(s) { return {0:'待上链',1:'上链成功',2:'上链失败'}[s] || '未知'; }
    }
});
