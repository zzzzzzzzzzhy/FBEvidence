new Vue({
    el: '#app',
    data() {
        return {
            // 加载状态
            loading: {
                createRepo: false,
                createBranch: false,
                upload: false,
                table: false,
                merge: false
            },
            // 拖拽状态
            isDragOver: false,
            // 仓库表单
            repositoryForm: {
                groupName: '',
                projectName: '',
                description: ''
            },
            // 表单验证规则
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
            // 分支表单
            branchForm: {
                branchName: '',
                baseBranch: ''
            },
            // 分支合并表单
            mergeForm: {
                sourceBranch: '',
                targetBranch: ''
            },
            // 上传表单
            uploadForm: {
                repositoryId: '',
                branchName: 'main',
                commitMessage: '',
                uploadMode: 'single'
            },
            // Tab激活状态
            branchTabActive: 'create',
            // 数据列表
            repositories: [],
            branches: [],
            evidenceList: [],
            fileList: [],
            selectedRepository: '',
            // 分页
            pagination: {
                page: 1,
                size: 10,
                total: 0
            },
            // 统计信息
            stats: {
                totalRepositories: 0,
                totalBranches: 0,
                totalCommits: 0,
                todayCommits: 0
            }
        }
    },
    mounted() {
        this.loadRepositories();
        this.loadEvidenceList();
        this.loadStats();
    },
    methods: {
        // 创建仓库
        async createRepository() {
            // 使用表单验证
            this.$refs.repositoryForm.validate((valid) => {
                if (!valid) {
                    return false;
                }
            });
            
            // 检查仓库是否已存在
            const exists = this.repositories.find(repo => 
                repo.groupName === this.repositoryForm.groupName && 
                repo.projectName === this.repositoryForm.projectName
            );
            if (exists) {
                this.$message.error('仓库已存在');
                return;
            }
            
            this.loading.createRepo = true;
            try {
                const response = await this.request('POST', '/api/code/repository', this.repositoryForm);
                if (response.code === 200) {
                    this.$message.success('仓库创建成功');
                    this.repositoryForm = { groupName: '', projectName: '', description: '' };
                    this.loadRepositories();
                } else {
                    this.$message.error(response.message || '创建失败');
                }
            } catch (error) {
                console.error('创建仓库失败:', error);
                this.$message.error('创建失败：' + error.message);
            } finally {
                this.loading.createRepo = false;
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
            
            this.loading.createBranch = true;
            try {
                const params = {
                    repositoryId: this.selectedRepository,
                    branchName: this.branchForm.branchName,
                    baseBranch: this.branchForm.baseBranch || 'main'
                };
                
                const response = await this.request('POST', '/api/code/branch', params);
                if (response.code === 200) {
                    this.$message.success('分支创建成功');
                    this.branchForm = { branchName: '', baseBranch: '' };
                    this.loadBranches();
                } else {
                    this.$message.error(response.message || '创建失败');
                }
            } catch (error) {
                console.error('创建分支失败:', error);
                this.$message.error('创建失败：' + error.message);
            } finally {
                this.loading.createBranch = false;
            }
        },
        
        // 上传代码
        async uploadCode() {
            if (!this.uploadForm.repositoryId || !this.uploadForm.branchName || this.fileList.length === 0) {
                this.$message.error('请选择仓库、分支并上传文件');
                return;
            }
            
            if (!this.uploadForm.commitMessage) {
                this.$message.error('请输入提交信息');
                return;
            }
            
            // 验证文件类型
            const validExtensions = ['.py', '.java', '.js', '.ts', '.html', '.css', '.sql', '.json', '.xml', '.c', '.cpp', '.h', '.hpp', '.go', '.rs', '.php', '.rb', '.swift', '.kt', '.scala', '.sol', '.vue', '.jsx', '.tsx'];
            const invalidFiles = this.fileList.filter(file => {
                const ext = '.' + file.name.split('.').pop().toLowerCase();
                return !validExtensions.includes(ext);
            });
            
            if (invalidFiles.length > 0) {
                this.$message.error(`不支持的文件类型: ${invalidFiles.map(f => f.name).join(', ')}`);
                return;
            }
            
            // 验证文件大小
            const maxSize = 10 * 1024 * 1024; // 10MB
            const oversizedFiles = this.fileList.filter(file => file.size > maxSize);
            if (oversizedFiles.length > 0) {
                this.$message.error(`文件过大(超过10MB): ${oversizedFiles.map(f => f.name).join(', ')}`);
                return;
            }
            
            this.loading.upload = true;
            
            try {
                if (this.uploadForm.uploadMode === 'batch') {
                    // 批量上传模式
                    await this.uploadCodeBatch();
                } else {
                    // 单文件上传模式
                    await this.uploadCodeSingle();
                }
            } catch (error) {
                console.error('上传代码失败:', error);
                this.$message.error('上传失败：' + error.message);
            } finally {
                this.loading.upload = false;
            }
        },

        // 单文件上传
        async uploadCodeSingle() {
            let successCount = 0;
            let failCount = 0;
            
            for (let fileItem of this.fileList) {
                try {
                    const formData = new FormData();
                    formData.append('repositoryId', this.uploadForm.repositoryId);
                    formData.append('branchName', this.uploadForm.branchName);
                    formData.append('file', fileItem.raw);
                    formData.append('fileName', fileItem.name);
                    formData.append('commitMessage', this.uploadForm.commitMessage || `上传文件: ${fileItem.name}`);
                    
                    const response = await this.requestFormData('POST', '/api/code/commit', formData);
                    
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
            
            this.handleUploadResult(successCount, failCount);
        },

        // 批量上传
        async uploadCodeBatch() {
            try {
                const formData = new FormData();
                formData.append('repositoryId', this.uploadForm.repositoryId);
                formData.append('branchName', this.uploadForm.branchName);
                formData.append('commitMessage', this.uploadForm.commitMessage);
                
                // 添加所有文件
                for (let fileItem of this.fileList) {
                    formData.append('files', fileItem.raw);
                }
                
                const response = await this.requestFormData('POST', '/api/code/commit-batch', formData);
                
                if (response.code === 200) {
                    this.$message.success(`批量提交成功：${this.fileList.length} 个文件`);
                    this.clearFiles();
                    this.uploadForm.commitMessage = '';
                    this.loadEvidenceList();
                    this.loadStats();
                } else {
                    this.$message.error(response.message || '批量提交失败');
                }
            } catch (error) {
                console.error('批量上传异常:', error);
                this.$message.error('批量提交失败：' + error.message);
                throw error;
            }
        },

        // 处理上传结果
        handleUploadResult(successCount, failCount) {
            // 显示上传结果
            if (successCount > 0 && failCount === 0) {
                this.$message.success(`成功上传 ${successCount} 个文件`);
            } else if (successCount > 0 && failCount > 0) {
                this.$message.warning(`成功上传 ${successCount} 个文件，${failCount} 个文件上传失败`);
            } else {
                this.$message.error(`所有文件上传失败`);
            }
            
            if (successCount > 0) {
                this.clearFiles();
                this.uploadForm.commitMessage = '';
                this.loadEvidenceList();
                this.loadStats();
            }
        },

        // 合并分支
        async mergeBranch() {
            if (!this.selectedRepository || !this.mergeForm.sourceBranch || !this.mergeForm.targetBranch) {
                this.$message.error('请选择仓库和分支');
                return;
            }
            
            if (this.mergeForm.sourceBranch === this.mergeForm.targetBranch) {
                this.$message.error('源分支和目标分支不能相同');
                return;
            }
            
            // 确认合并操作
            try {
                await this.$confirm(
                    `确认将分支 "${this.mergeForm.sourceBranch}" 合并到 "${this.mergeForm.targetBranch}" 吗？`,
                    '分支合并确认',
                    {
                        type: 'warning',
                        confirmButtonText: '确认合并',
                        cancelButtonText: '取消'
                    }
                );
            } catch {
                return; // 用户取消
            }
            
            this.loading.merge = true;
            try {
                const params = {
                    repositoryId: this.selectedRepository,
                    sourceBranch: this.mergeForm.sourceBranch,
                    targetBranch: this.mergeForm.targetBranch
                };
                
                const response = await this.request('POST', '/api/code/merge', params);
                if (response.code === 200) {
                    this.$message.success(`分支合并成功：${this.mergeForm.sourceBranch} → ${this.mergeForm.targetBranch}`);
                    this.mergeForm = { sourceBranch: '', targetBranch: '' };
                    this.loadBranches();
                    this.loadEvidenceList();
                    this.loadStats();
                } else {
                    this.$message.error(response.message || '分支合并失败');
                }
            } catch (error) {
                console.error('合并分支失败:', error);
                this.$message.error('合并失败：' + error.message);
            } finally {
                this.loading.merge = false;
            }
        },
        
        // 加载仓库列表
        async loadRepositories() {
            try {
                console.log('开始加载仓库列表...');
                const response = await this.request('GET', '/api/code/repositories');
                console.log('仓库列表API响应:', response);
                if (response.code === 200) {
                    this.repositories = response.data || [];
                    console.log('设置repositories数据:', this.repositories);
                    console.log('repositories数组长度:', this.repositories.length);
                } else {
                    console.error('API返回错误:', response);
                    this.$message.error('加载仓库列表失败：' + (response.message || '未知错误'));
                }
            } catch (error) {
                console.error('加载仓库列表失败:', error);
                this.$message.error('加载仓库列表失败：' + error.message);
            }
        },
        
        // 加载分支列表
        async loadBranches() {
            if (!this.selectedRepository) {
                this.branches = [];
                return;
            }
            
            try {
                const response = await this.request('GET', `/api/code/branches?repositoryId=${this.selectedRepository}`);
                if (response.code === 200) {
                    this.branches = response.data || [];
                    // 如果没有选中分支且有分支列表，默认选中main分支
                    if (!this.uploadForm.branchName && this.branches.length > 0) {
                        const mainBranch = this.branches.find(b => b.branchName === 'main');
                        this.uploadForm.branchName = mainBranch ? 'main' : this.branches[0].branchName;
                    }
                }
            } catch (error) {
                console.error('加载分支列表失败:', error);
            }
        },
        
        // 加载存证记录
        async loadEvidenceList() {
            this.loading.table = true;
            try {
                const response = await this.request('GET', '/api/code/evidence');
                if (response.code === 200) {
                    this.evidenceList = response.data || [];
                    this.pagination.total = this.evidenceList.length;
                } else {
                    this.$message.error('加载存证记录失败：' + response.message);
                }
            } catch (error) {
                console.error('加载存证记录失败:', error);
                this.$message.error('加载存证记录失败，请检查网络连接');
            } finally {
                this.loading.table = false;
            }
        },
        
        // 加载统计信息
        async loadStats() {
            // 模拟统计数据，实际应该从后端获取
            this.stats = {
                totalRepositories: this.repositories.length,
                totalBranches: this.branches.length,
                totalCommits: this.evidenceList.length,
                todayCommits: this.evidenceList.filter(item => {
                    const today = new Date().toDateString();
                    const itemDate = new Date(item.createdAt).toDateString();
                    return today === itemDate;
                }).length
            };
        },
        
        // 仓库变更
        onRepositoryChange() {
            this.uploadForm.repositoryId = this.selectedRepository;
            this.loadBranches();
        },
        
        // 文件选择
        handleFileSelect(file, fileList) {
            this.fileList = fileList;
        },
        
        // 拖拽处理
        handleDrop(e) {
            e.preventDefault();
            this.isDragOver = false;
            
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                for (let i = 0; i < files.length; i++) {
                    const file = files[i];
                    // 检查文件类型
                    const validExtensions = ['.py', '.java', '.js', '.ts', '.html', '.css', '.sql', '.json', '.xml', '.c', '.cpp', '.h', '.hpp', '.go', '.rs', '.php', '.rb', '.swift', '.kt', '.scala', '.sol'];
                    const fileExt = '.' + file.name.split('.').pop().toLowerCase();
                    
                    if (validExtensions.includes(fileExt)) {
                        this.fileList.push({
                            name: file.name,
                            size: file.size,
                            raw: file,
                            status: 'ready'
                        });
                    } else {
                        this.$message.warning(`不支持的文件类型: ${file.name}`);
                    }
                }
            }
        },
        
        handleDragOver(e) {
            e.preventDefault();
            this.isDragOver = true;
        },
        
        handleDragLeave(e) {
            e.preventDefault();
            this.isDragOver = false;
        },
        
        // 清空文件
        clearFiles() {
            this.fileList = [];
            this.$refs.upload.clearFiles();
        },
        
        // 查看详情
        viewDetails(row) {
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
        
        // 分页处理
        handlePageChange(page) {
            this.pagination.page = page;
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
        
        // HTTP请求封装
        async request(method, url, data = null) {
            const options = {
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                },
            };
            
            // 添加认证头（如果有token）
            const token = localStorage.getItem('token');
            if (token) {
                options.headers['Authorization'] = `Bearer ${token}`;
            }
            
            console.log(`发起${method}请求:`, url, options);
            
            // 处理GET请求参数
            if (method === 'GET' && data) {
                const params = new URLSearchParams(data);
                url += '?' + params.toString();
            } else if (data) {
                // POST/PUT请求，将数据放在body中
                if (method === 'POST' && url.includes('repository')) {
                    // 仓库创建使用FormData
                    const formData = new FormData();
                    Object.keys(data).forEach(key => {
                        if (data[key] !== null && data[key] !== undefined) {
                            formData.append(key, data[key]);
                        }
                    });
                    options.body = formData;
                    delete options.headers['Content-Type']; // 让浏览器自动设置Content-Type
                } else if (method === 'POST' && url.includes('branch')) {
                    // 分支创建使用FormData
                    const formData = new FormData();
                    Object.keys(data).forEach(key => {
                        if (data[key] !== null && data[key] !== undefined) {
                            formData.append(key, data[key]);
                        }
                    });
                    options.body = formData;
                    delete options.headers['Content-Type'];
                } else if (method === 'POST' && url.includes('merge')) {
                    // 分支合并使用FormData
                    const formData = new FormData();
                    Object.keys(data).forEach(key => {
                        if (data[key] !== null && data[key] !== undefined) {
                            formData.append(key, data[key]);
                        }
                    });
                    options.body = formData;
                    delete options.headers['Content-Type'];
                } else {
                    options.body = JSON.stringify(data);
                }
            }
            
            const response = await fetch(url, options);
            console.log(`${method}请求响应状态:`, response.status, response.statusText);
            if (!response.ok) {
                const errorText = await response.text();
                console.error(`请求失败 ${url}:`, response.status, errorText);
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            const jsonResponse = await response.json();
            console.log(`${method}请求响应数据:`, jsonResponse);
            return jsonResponse;
        },
        
        // FormData请求封装
        async requestFormData(method, url, formData) {
            const options = {
                method: method,
                body: formData
            };
            
            // 添加认证头（如果有token）
            const token = localStorage.getItem('token');
            if (token) {
                options.headers = {
                    'Authorization': `Bearer ${token}`
                };
            }
            
            const response = await fetch(url, options);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return await response.json();
        }
    }
});