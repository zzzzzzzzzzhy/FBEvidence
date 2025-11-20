// 使用手册交互功能
new Vue({
    el: '#app',
    data() {
        return {
            searchKeyword: '',
            activeSection: 'overview',
            searchResults: [],
            isSearching: false
        }
    },
    mounted() {
        this.initScrollListener();
        this.initSmoothScroll();
    },
    methods: {
        // 返回主页
        goHome() {
            window.location.href = '/static/index.html';
        },

        // 搜索内容
        searchContent() {
            if (!this.searchKeyword.trim()) {
                this.clearSearchHighlight();
                return;
            }

            this.isSearching = true;
            this.clearSearchHighlight();

            const keyword = this.searchKeyword.trim().toLowerCase();
            const content = this.$refs.content;
            const textNodes = this.getTextNodes(content);
            let matchCount = 0;

            textNodes.forEach(node => {
                const text = node.textContent.toLowerCase();
                if (text.includes(keyword)) {
                    this.highlightText(node, keyword);
                    matchCount++;
                }
            });

            this.isSearching = false;

            if (matchCount === 0) {
                this.showSearchResult('未找到相关内容');
            } else {
                this.showSearchResult(`找到 ${matchCount} 处相关内容`);
                // 滚动到第一个匹配项
                const firstHighlight = content.querySelector('.search-highlight');
                if (firstHighlight) {
                    firstHighlight.scrollIntoView({
                        behavior: 'smooth',
                        block: 'center'
                    });
                }
            }
        },

        // 获取所有文本节点
        getTextNodes(element) {
            const textNodes = [];
            const walker = document.createTreeWalker(
                element,
                NodeFilter.SHOW_TEXT,
                {
                    acceptNode: function(node) {
                        // 跳过script和style标签内的文本
                        const parent = node.parentElement;
                        if (parent && (parent.tagName === 'SCRIPT' || parent.tagName === 'STYLE')) {
                            return NodeFilter.FILTER_REJECT;
                        }
                        // 只处理有实际内容的文本节点
                        if (node.textContent.trim().length > 0) {
                            return NodeFilter.FILTER_ACCEPT;
                        }
                        return NodeFilter.FILTER_REJECT;
                    }
                }
            );

            let node;
            while (node = walker.nextNode()) {
                textNodes.push(node);
            }
            return textNodes;
        },

        // 高亮文本
        highlightText(textNode, keyword) {
            const text = textNode.textContent;
            const lowerText = text.toLowerCase();
            const lowerKeyword = keyword.toLowerCase();

            if (lowerText.includes(lowerKeyword)) {
                const parent = textNode.parentElement;
                const fragment = document.createDocumentFragment();
                let lastIndex = 0;
                let index = lowerText.indexOf(lowerKeyword);

                while (index !== -1) {
                    // 添加关键词前的文本
                    if (index > lastIndex) {
                        const beforeText = document.createTextNode(text.substring(lastIndex, index));
                        fragment.appendChild(beforeText);
                    }

                    // 添加高亮的关键词
                    const highlightSpan = document.createElement('span');
                    highlightSpan.className = 'search-highlight';
                    highlightSpan.textContent = text.substring(index, index + keyword.length);
                    fragment.appendChild(highlightSpan);

                    lastIndex = index + keyword.length;
                    index = lowerText.indexOf(lowerKeyword, lastIndex);
                }

                // 添加剩余文本
                if (lastIndex < text.length) {
                    const afterText = document.createTextNode(text.substring(lastIndex));
                    fragment.appendChild(afterText);
                }

                parent.replaceChild(fragment, textNode);
            }
        },

        // 清除搜索高亮
        clearSearchHighlight() {
            const highlights = this.$refs.content.querySelectorAll('.search-highlight');
            highlights.forEach(highlight => {
                const parent = highlight.parentElement;
                parent.replaceChild(document.createTextNode(highlight.textContent), highlight);
                // 合并相邻的文本节点
                parent.normalize();
            });
        },

        // 显示搜索结果提示
        showSearchResult(message) {
            // 可以在这里添加搜索结果提示的UI
            console.log(message);
        },

        // 滚动到指定章节
        scrollToSection(sectionId) {
            this.activeSection = sectionId;
            const element = document.getElementById(sectionId);
            if (element) {
                // 清除搜索高亮
                this.clearSearchHighlight();
                this.searchKeyword = '';

                // 平滑滚动到目标元素
                element.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            }
        },

        // 初始化滚动监听
        initScrollListener() {
            const sections = [
                'overview', 'quickstart', 'modules', 'file-evidence',
                'code-evidence', 'query-verify', 'blockchain-info',
                'did-tools', 'rules', 'guide', 'faq'
            ];

            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const id = entry.target.id;
                        if (sections.includes(id)) {
                            this.activeSection = id;
                        }
                    }
                });
            }, {
                rootMargin: '-100px 0px -70% 0px',
                threshold: 0.1
            });

            sections.forEach(sectionId => {
                const element = document.getElementById(sectionId);
                if (element) {
                    observer.observe(element);
                }
            });
        },

        // 初始化平滑滚动
        initSmoothScroll() {
            // 为所有内部锚点链接添加平滑滚动
            const links = document.querySelectorAll('a[href^="#"]');
            links.forEach(link => {
                link.addEventListener('click', (e) => {
                    e.preventDefault();
                    const targetId = link.getAttribute('href').substring(1);
                    this.scrollToSection(targetId);
                });
            });
        },

        // 键盘事件处理
        handleKeydown(event) {
            // Ctrl+F 或 Cmd+F 触发搜索
            if ((event.ctrlKey || event.metaKey) && event.key === 'f') {
                event.preventDefault();
                const searchInput = document.querySelector('.search-input');
                if (searchInput) {
                    searchInput.focus();
                    searchInput.select();
                }
            }

            // ESC 清除搜索
            if (event.key === 'Escape') {
                this.clearSearchHighlight();
                this.searchKeyword = '';
            }

            // Enter 执行搜索
            if (event.key === 'Enter' && document.activeElement === document.querySelector('.search-input')) {
                this.searchContent();
            }
        },

        // 复制代码到剪贴板
        copyCode(text) {
            if (navigator.clipboard) {
                navigator.clipboard.writeText(text).then(() => {
                    this.showToast('代码已复制到剪贴板');
                }).catch(() => {
                    this.fallbackCopyText(text);
                });
            } else {
                this.fallbackCopyText(text);
            }
        },

        // 兼容性复制方法
        fallbackCopyText(text) {
            const textArea = document.createElement('textarea');
            textArea.value = text;
            textArea.style.position = 'fixed';
            textArea.style.left = '-999999px';
            textArea.style.top = '-999999px';
            document.body.appendChild(textArea);
            textArea.focus();
            textArea.select();

            try {
                document.execCommand('copy');
                this.showToast('代码已复制到剪贴板');
            } catch (err) {
                console.error('复制失败:', err);
                this.showToast('复制失败，请手动复制');
            }

            document.body.removeChild(textArea);
        },

        // 显示提示消息
        showToast(message) {
            // 创建提示元素
            const toast = document.createElement('div');
            toast.textContent = message;
            toast.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                background: #409eff;
                color: white;
                padding: 12px 20px;
                border-radius: 6px;
                box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                z-index: 10000;
                font-size: 14px;
                opacity: 0;
                transform: translateY(-10px);
                transition: all 0.3s ease;
            `;

            document.body.appendChild(toast);

            // 显示动画
            setTimeout(() => {
                toast.style.opacity = '1';
                toast.style.transform = 'translateY(0)';
            }, 100);

            // 自动隐藏
            setTimeout(() => {
                toast.style.opacity = '0';
                toast.style.transform = 'translateY(-10px)';
                setTimeout(() => {
                    if (toast.parentNode) {
                        document.body.removeChild(toast);
                    }
                }, 300);
            }, 3000);
        },

        // 打印手册
        printManual() {
            window.print();
        },

        // 导出为PDF（需要浏览器支持）
        exportToPDF() {
            if (window.chrome) {
                // Chrome浏览器可以直接打印为PDF
                this.printManual();
                this.showToast('请在打印对话框中选择"另存为PDF"');
            } else {
                this.showToast('请使用Chrome浏览器导出PDF功能');
            }
        },

        // 获取当前章节进度
        getReadingProgress() {
            const content = this.$refs.content;
            if (!content) return 0;

            const scrollTop = content.scrollTop;
            const scrollHeight = content.scrollHeight - content.clientHeight;
            const progress = (scrollTop / scrollHeight) * 100;

            return Math.min(Math.max(progress, 0), 100);
        }
    },

    // 生命周期钩子
    created() {
        // 监听键盘事件
        document.addEventListener('keydown', this.handleKeydown);
    },

    beforeDestroy() {
        // 清理事件监听器
        document.removeEventListener('keydown', this.handleKeydown);
    },

    // 计算属性
    computed: {
        // 搜索按钮文本
        searchButtonText() {
            return this.isSearching ? '⟳' : '🔍';
        },

        // 当前章节标题
        currentSectionTitle() {
            const sectionTitles = {
                'overview': '系统概述',
                'quickstart': '快速入门',
                'modules': '功能模块',
                'file-evidence': '文件存证',
                'code-evidence': '代码存证',
                'query-verify': '查询验证',
                'blockchain-info': '区块链信息',
                'did-tools': 'DID工具箱',
                'rules': '上传存证规则',
                'guide': '操作指南',
                'faq': '常见问题'
            };
            return sectionTitles[this.activeSection] || '';
        }
    },

    // 监听器
    watch: {
        // 监听搜索关键词变化
        searchKeyword(newVal, oldVal) {
            if (newVal !== oldVal && !newVal.trim()) {
                this.clearSearchHighlight();
            }
        }
    }
});

// 页面加载完成后的初始化
document.addEventListener('DOMContentLoaded', function() {
    // 添加平滑滚动支持
    document.documentElement.style.scrollBehavior = 'smooth';

    // 添加代码块复制功能
    const codeBlocks = document.querySelectorAll('code');
    codeBlocks.forEach(codeBlock => {
        if (codeBlock.textContent.length > 20) {
            codeBlock.style.cursor = 'pointer';
            codeBlock.title = '点击复制代码';
            codeBlock.addEventListener('click', function() {
                const text = this.textContent;
                navigator.clipboard.writeText(text).then(() => {
                    console.log('代码已复制');
                }).catch(() => {
                    console.log('复制失败');
                });
            });
        }
    });

    // 添加返回顶部按钮
    const backToTopButton = document.createElement('button');
    backToTopButton.innerHTML = '↑';
    backToTopButton.style.cssText = `
        position: fixed;
        bottom: 30px;
        right: 30px;
        width: 50px;
        height: 50px;
        border-radius: 50%;
        background: linear-gradient(135deg, #409eff, #66b1ff);
        color: white;
        border: none;
        cursor: pointer;
        font-size: 18px;
        font-weight: bold;
        box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
        z-index: 1000;
        opacity: 0;
        transform: translateY(20px);
        transition: all 0.3s ease;
        display: none;
    `;

    document.body.appendChild(backToTopButton);

    // 滚动显示/隐藏返回顶部按钮
    window.addEventListener('scroll', function() {
        if (window.scrollY > 300) {
            backToTopButton.style.display = 'block';
            setTimeout(() => {
                backToTopButton.style.opacity = '1';
                backToTopButton.style.transform = 'translateY(0)';
            }, 100);
        } else {
            backToTopButton.style.opacity = '0';
            backToTopButton.style.transform = 'translateY(20px)';
            setTimeout(() => {
                if (window.scrollY <= 300) {
                    backToTopButton.style.display = 'none';
                }
            }, 300);
        }
    });

    // 返回顶部功能
    backToTopButton.addEventListener('click', function() {
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
    });
});

// 工具函数
const ManualUtils = {
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

    // 格式化文件大小
    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    },

    // 验证DID格式
    validateDID(did) {
        const didPattern = /^did:evidence:[a-f0-9]{16}$/;
        return didPattern.test(did);
    }
};