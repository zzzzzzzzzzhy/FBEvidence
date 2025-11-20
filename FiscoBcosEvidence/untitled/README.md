# 区块链存证系统

基于FISCO BCOS的企业级区块链存证系统，提供文件上链存证、查询验证等功能。

## 📋 目录

- [功能特性](#功能特性)
- [技术架构](#技术架构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API文档](#api文档)
- [部署指南](#部署指南)
- [常见问题](#常见问题)
- [贡献指南](#贡献指南)

## 🚀 功能特性

- ✅ **文件上传存证**: 支持多种文件格式，自动计算文件哈希
- ✅ **区块链存储**: 基于FISCO BCOS，数据不可篡改
- ✅ **存证查询**: 支持多维度查询和分页
- ✅ **存证验证**: 一键验证文件真实性
- ✅ **用户管理**: JWT认证，支持多用户
- ✅ **响应式前端**: 基于Vue.js + Element UI
- ✅ **RESTful API**: 标准化接口设计
- ✅ **国密支持**: 支持SM2/SM3/SM4国密算法

## 🏗️ 技术架构

### 后端技术栈
- **Java 8+**: 开发语言
- **Spring Boot 2.7.x**: 应用框架
- **MyBatis Plus**: ORM框架
- **MySQL 8.0**: 关系型数据库
- **FISCO BCOS Java SDK**: 区块链交互
- **JWT**: 身份认证
- **Hutool**: 工具库

### 前端技术栈
- **Vue.js 2.x**: 前端框架
- **Element UI**: UI组件库
- **Axios**: HTTP客户端
- **Thymeleaf**: 模板引擎

### 区块链技术
- **FISCO BCOS 2.x/3.x**: 联盟链平台
- **Solidity**: 智能合约语言
- **Web3j**: 以太坊Java库

## 📋 环境要求

### 基础环境
- **JDK**: 1.8 或更高版本
- **Maven**: 3.6+ 或 Gradle 6+
- **MySQL**: 8.0+
- **Node.js**: 14+ (可选，用于前端开发)

### 区块链环境
- **FISCO BCOS**: 2.9.1+ 或 3.x 版本
- **控制台**: 用于合约部署和管理

## 🚀 快速开始

### 1. 环境准备

#### 1.1 安装FISCO BCOS
```bash
# 下载build_chain.sh脚本
curl -#LO https://github.com/FISCO-BCOS/FISCO-BCOS/releases/download/v2.9.1/build_chain.sh

# 构建4节点区块链网络
bash build_chain.sh -l 127.0.0.1:4 -p 30300,20200,8545

# 启动节点
bash nodes/127.0.0.1/start_all.sh
```

#### 1.2 安装控制台
```bash
# 下载控制台
curl -#LO https://github.com/FISCO-BCOS/console/releases/download/v2.9.2/download_console.sh
bash download_console.sh

# 配置控制台
cp nodes/127.0.0.1/sdk/* console/conf/
```

#### 1.3 创建数据库
```sql
CREATE DATABASE blockchain_evidence DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 项目部署

#### 2.1 克隆项目
```bash
git clone <repository-url>
cd blockchain-evidence-system
```

#### 2.2 配置数据库
修改 `src/main/resources/application-dev.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blockchain_evidence
    username: your_username
    password: your_password
```

#### 2.3 配置FISCO BCOS
1. 复制SDK证书到项目
```bash
mkdir -p src/main/resources/conf
cp ~/fisco/nodes/127.0.0.1/sdk/* src/main/resources/conf/
```

2. 修改 `src/main/resources/config.toml`:
```toml
[network]
peers = ["127.0.0.1:20200", "127.0.0.1:20201"]
defaultGroup = "group0"
```

#### 2.4 编译合约
```bash
# 将合约文件放入控制台
cp src/main/resources/contracts/EvidenceContract.sol ~/fisco/console/contracts/solidity/

# 编译合约
cd ~/fisco/console
bash sol2java.sh -p com.evidence.contracts

# 将生成的Java文件复制到项目
cp contracts/sdk/java/com/evidence/contracts/EvidenceContract.java \
   <project>/src/main/java/com/evidence/contracts/
```

#### 2.5 部署合约
```bash
# 在控制台中部署合约
deploy EvidenceContract

# 记录合约地址，更新application.yml中的contract-address配置
```

#### 2.6 启动应用
```bash
# Maven方式
mvn spring-boot:run

# 或者
mvn clean package
java -jar target/blockchain-evidence-system-1.0.0.jar
```

### 3. 访问系统
- 系统地址: http://localhost:8080
- 默认账号: admin / 123123

## ⚙️ 配置说明

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blockchain_evidence
    username: root
    password: password
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
```

### FISCO BCOS配置
```yaml
fisco:
  config-file: config.toml
  group-id: group0
  contract-address: 0x1234567890123456789012345678901234567890
```

### 文件上传配置
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

## 📚 API文档

### 认证接口

#### 用户登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "123123"
}
```

#### 用户信息
```http
GET /api/auth/info
Authorization: Bearer <token>
```

### 存证接口

#### 文件上传存证
```http
POST /api/evidence/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <file>
description: 文件描述
hashAlgorithm: SHA256
```

#### 查询存证列表
```http
GET /api/evidence/list?current=1&size=10&fileName=test
Authorization: Bearer <token>
```

#### 获取存证详情
```http
GET /api/evidence/{id}
Authorization: Bearer <token>
```

#### 验证存证
```http
POST /api/evidence/verify?fileHash=<hash>
Authorization: Bearer <token>
```

### 区块链接口

#### 获取区块高度
```http
GET /api/blockchain/block-number
Authorization: Bearer <token>
```

#### 获取区块信息
```http
GET /api/blockchain/block/{blockNumber}
Authorization: Bearer <token>
```

## 🚀 部署指南

### Docker部署

#### 1. 构建镜像
```dockerfile
FROM openjdk:8-jre-alpine

VOLUME /tmp
COPY target/blockchain-evidence-system-1.0.0.jar app.jar
COPY src/main/resources/conf /app/conf

ENTRYPOINT ["java","-jar","/app.jar"]
EXPOSE 8080
```

```bash
docker build -t blockchain-evidence-system .
```

#### 2. 运行容器
```bash
docker run -d \
  --name evidence-system \
  -p 8080:8080 \
  -v /path/to/uploads:/app/uploads \
  -e SPRING_PROFILES_ACTIVE=prod \
  blockchain-evidence-system
```

### 生产环境部署

#### 1. 系统配置
```bash
# 创建应用目录
sudo mkdir -p /opt/evidence-system
sudo chown -R app:app /opt/evidence-system

# 创建日志目录
sudo mkdir -p /var/log/evidence-system
sudo chown -R app:app /var/log/evidence-system

# 创建上传目录
sudo mkdir -p /data/uploads
sudo chown -R app:app /data/uploads
```

#### 2. 配置文件
```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:mysql://db-server:3306/blockchain_evidence
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

server:
  port: 8080

logging:
  file:
    name: /var/log/evidence-system/application.log
  level:
    root: info
```

#### 3. 服务配置
```ini
# /etc/systemd/system/evidence-system.service
[Unit]
Description=Blockchain Evidence System
After=network.target

[Service]
Type=simple
User=app
WorkingDirectory=/opt/evidence-system
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=prod blockchain-evidence-system-1.0.0.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# 启动服务
sudo systemctl enable evidence-system
sudo systemctl start evidence-system
```

#### 4. Nginx反向代理
```nginx
server {
    listen 80;
    server_name evidence.example.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /uploads/ {
        alias /data/uploads/;
        expires 30d;
    }
}
```

## 🔧 开发指南

### 本地开发环境搭建
```bash
# 1. 克隆项目
git clone <repository-url>
cd blockchain-evidence-system

# 2. 安装依赖
mvn clean install

# 3. 配置开发环境
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml

# 4. 启动开发服务器
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 前端开发
```bash
# 如果需要单独开发前端
cd frontend
npm install
npm run serve
```

### 智能合约开发
1. 修改 `src/main/resources/contracts/EvidenceContract.sol`
2. 使用控制台编译合约
3. 更新Java合约包装类
4. 重新部署合约

### 测试
```bash
# 运行单元测试
mvn test

# 运行集成测试
mvn test -Dtest=*IntegrationTest

# 生成测试报告
mvn jacoco:report
```

## ❓ 常见问题

### Q1: 连接FISCO BCOS失败
**A**: 检查以下配置：
- 节点是否正常启动
- 证书文件是否正确复制
- config.toml中的节点地址和端口
- 防火墙设置

### Q2: 合约调用失败
**A**: 可能的原因：
- 合约地址配置错误
- 合约版本不匹配
- 账户权限不足
- Gas不足

### Q3: 文件上传失败
**A**: 检查：
- 文件大小是否超过限制
- 文件类型是否支持
- 磁盘空间是否充足
- 上传目录权限

### Q4: JWT令牌失效
**A**: 检查：
- 令牌是否过期
- 密钥配置是否正确
- 时间同步问题

## 📄 许可证

本项目采用 [MIT License](LICENSE) 许可证。

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📞 技术支持

- 项目地址: [GitHub Repository]
- 问题反馈: [Issues]
- 技术交流: [Discussions]

## 🏷️ 版本历史

### v1.0.0 (2024-01-01)
- 初始版本发布
- 基础存证功能
- 用户认证系统
- 前端界面

### v1.1.0 (计划中)
- 批量上传功能
- 权限管理
- 审计日志
- 数据导出



---

**注意**: 本系统仅供学习和测试使用，生产环境部署请注意安全配置和性能优化。