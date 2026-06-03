# FISCO BCOS 源码阅读笔记

> 只讲本项目用到的部分，从配置初始化 → SDK连接 → 合约调用 → 异步上链，完整走一遍。

---

## 目录

1. [整体架构：Java 怎么和链通信](#1-整体架构java-怎么和链通信)
2. [config.toml：节点连接配置文件](#2-configtoml节点连接配置文件)
3. [FiscoBcosConfig：SDK 初始化三件套](#3-fiscobcosconfigsdk-初始化三件套)
4. [BcosSDK：SDK 入口类](#4-bcossdksdk-入口类)
5. [Client：链上操作的执行者](#5-client链上操作的执行者)
6. [CryptoKeyPair：账户与签名](#6-cryptokeypair账户与签名)
7. [国密（GM）：useSMCrypto 做了什么](#7-国密gmusmcrypto-做了什么)
8. [EvidenceContract：合约 Java 包装类](#8-evidencecontract合约-java-包装类)
9. [ABI：Java 和 Solidity 之间的翻译协议](#9-abijava-和-solidity-之间的翻译协议)
10. [TransactionReceipt：一笔交易的回执](#10-transactionreceipt一笔交易的回执)
11. [BlockchainServiceImpl：业务层怎么调链](#11-blockchainserviceimpl业务层怎么调链)
12. [AsyncTaskServiceImpl：异步上链架构](#12-asynctaskserviceimpl异步上链架构)
13. [Redis 队列：任务怎么传递](#13-redis-队列任务怎么传递)
14. [@Lazy 注解：为什么要延迟初始化](#14-lazy-注解为什么要延迟初始化)
15. [完整上链流程时序图](#15-完整上链流程时序图)

---

## 1. 整体架构：Java 怎么和链通信

FISCO BCOS 的 Java SDK（`java-sdk 2.x`）是一个封装了底层 P2P 通信的客户端库。
Java 程序不直接连 RPC，而是走 **Channel 协议**（类似 WebSocket，支持国密 TLS）。

```
Spring Boot 应用
    │
    ▼
BcosSDK（读 config.toml，管理连接池）
    │
    ▼
Client（绑定某个群组 group1，发送交易/查询）
    │  Channel 协议（国密 TLS）
    ▼
FISCO BCOS 节点（4个，ports 20200~20203）
    │
    ▼
区块链（EvidenceContract 合约）
```

**关键对象只有三个：**

| 对象 | 类比 | 职责 |
|------|------|------|
| `BcosSDK` | 数据库连接池 | 管理与节点的底层连接 |
| `Client` | 数据库连接 | 绑定群组，执行具体操作 |
| `CryptoKeyPair` | 账户私钥 | 给每笔交易签名（证明身份） |

---

## 2. config.toml：节点连接配置文件

文件路径：`src/main/resources/config.toml`

```toml
[cryptoMaterial]
certPath = "conf"        # 证书目录（SDK 需要证书才能建立 TLS 连接）
useSMCrypto = true       # 使用国密算法（SM2/SM3/SM4），必须和节点配置一致

[network]
peers = [                # SDK 会轮询这 4 个节点，有一个通就行
    "127.0.0.1:20200",
    "127.0.0.1:20201",
    "127.0.0.1:20202",
    "127.0.0.1:20203"
]

[account]
keyStoreDir = "account"  # 账户密钥文件存储目录
accountFileFormat = "pem"
password = ""            # 账户文件密码（空 = 不加密）
accountAddress = ""      # 留空 = 每次启动自动生成新地址

[threadPool]
channelProcessorThreadSize = "16"   # 处理节点消息的线程数
receiptProcessorThreadSize = "16"   # 处理交易回执的线程数
maxBlockingQueueSize = "102400"     # 任务队列最大长度
```

### 证书目录结构（`conf/`）

FISCO BCOS 用 TLS 双向认证，SDK 必须持有合法证书才能连上节点。

```
conf/
├── ca.crt          # 链的根证书（CA，Certificate Authority）
├── sdk.crt         # SDK 的身份证书（ECDSA，标准版）
├── sdk.key         # SDK 的私钥（ECDSA）
└── gm/             # 国密证书（useSMCrypto=true 时使用）
    ├── gmca.crt    # 国密根证书
    ├── gmsdk.crt   # 国密签名证书
    ├── gmsdk.key   # 国密签名私钥（SM2）
    ├── gmensdk.crt # 国密加密证书（双证书机制）
    └── gmensdk.key # 国密加密私钥
```

**为什么国密有两套证书（`gmsdk` + `gmensdk`）？**

国密 SM2 规范要求签名和加密分开，使用两个独立的密钥对：
- `gmsdk` → 用于签名（证明身份）
- `gmensdk` → 用于加密（保护数据传输）

这是国密标准（GM/T 0009-2012）的要求，以太坊只用一套 ECDSA 密钥。

---

## 3. FiscoBcosConfig：SDK 初始化三件套

文件：`src/main/java/com/evidence/config/FiscoBcosConfig.java`

### 为什么需要把配置文件解压到临时目录？

SDK 的 `BcosSDK.build()` 需要读取**真实的文件系统路径**，
但 Spring Boot 打包成 fat JAR 后，`config.toml` 在 JAR 包内部，
路径格式是 `jar:file:///path/to/app.jar!/config.toml`，
操作系统无法直接打开这种路径。

解决方案：启动时把文件**解压到系统临时目录**，再把路径告诉 SDK。

```java
@Bean
@Lazy  // 延迟初始化，不在 Spring 启动时立即执行
public BcosSDK bcosSDK() {
    // 1. 在系统 /tmp 下创建临时目录，如 /tmp/fisco-conf-8374729/
    Path tmpDir = Files.createTempDirectory("fisco-conf-");

    // 2. 把 JAR 包内的证书文件解压到临时目录
    extractResource("conf/ca.crt",   tmpDir.resolve("conf/ca.crt"));
    extractResource("conf/sdk.crt",  tmpDir.resolve("conf/sdk.crt"));
    extractResource("conf/sdk.key",  tmpDir.resolve("conf/sdk.key"));

    // 国密证书放到 conf/gm/ 子目录（SDK 2.x 的约定）
    extractIfExists("conf/gmca.crt",    tmpDir.resolve("conf/gm/gmca.crt"));
    extractIfExists("conf/gmsdk.crt",   tmpDir.resolve("conf/gm/gmsdk.crt"));
    extractIfExists("conf/gmsdk.key",   tmpDir.resolve("conf/gm/gmsdk.key"));
    extractIfExists("conf/gmensdk.crt", tmpDir.resolve("conf/gm/gmensdk.crt"));
    extractIfExists("conf/gmensdk.key", tmpDir.resolve("conf/gm/gmensdk.key"));

    // 3. 读取 config.toml，把 certPath 改成临时目录的绝对路径
    //    原来：certPath = "conf"（相对路径，fat JAR 里找不到）
    //    改后：certPath = "/tmp/fisco-conf-8374729/conf"（绝对路径）
    String toml = new String(Files.readAllBytes(tomlFile));
    toml = toml.replace(
        "certPath = \"conf\"",
        "certPath = \"" + tmpDir.resolve("conf").toAbsolutePath() + "\""
    );
    Files.write(tomlFile, toml.getBytes());

    // 4. 用修改后的配置文件初始化 SDK
    BcosSDK sdk = BcosSDK.build(tomlFile.toAbsolutePath().toString());
    return sdk;
}
```

### 三个 Bean 的依赖关系

```
BcosSDK（读配置文件，建立连接）
    ↓ 作为参数传入
Client（绑定群组 group1）
    ↓ 作为参数传入
CryptoKeyPair（从 Client 的加密套件生成密钥对）
```

Spring 会自动按依赖顺序初始化，不需要手动控制顺序。

### Client Bean

```java
@Bean @Lazy
public Client client(BcosSDK bcosSDK) {
    // getClient(groupId) → 获取群组 1 的客户端
    // FISCO BCOS 支持多群组（类似不同的"频道"），这里只用 group1
    Client client = bcosSDK.getClient(groupId);
    return client;
}
```

### CryptoKeyPair Bean

```java
@Bean @Lazy
public CryptoKeyPair cryptoKeyPair(Client client) {
    // 从 Client 的加密套件获取密钥对
    // 因为 useSMCrypto=true，这里自动使用 SM2 算法
    // getCryptoKeyPair() 每次返回新生成的密钥对（不是固定的账户）
    CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();
    return keyPair;
}
```

---

## 4. BcosSDK：SDK 入口类

`org.fisco.bcos.sdk.BcosSDK` 是整个 SDK 的入口，内部做了这些事：

```
BcosSDK.build(configPath)
    │
    ├── 读取 config.toml
    ├── 加载证书（TLS 握手用）
    ├── 建立与各节点的 Channel 连接
    ├── 初始化加密套件（国密 or 标准）
    └── 创建线程池（channelProcessor + receiptProcessor）
```

**Channel 协议 vs RPC：**

以太坊 SDK 通常用 HTTP JSON-RPC（`eth_sendRawTransaction` 等）。
FISCO BCOS 的 Java SDK 用自己的 **Channel 协议**（基于 Netty 的长连接），
好处是：
- 持久连接，不用每次请求都握手
- 支持推送（节点主动通知客户端交易结果）
- 原生支持国密 TLS

---

## 5. Client：链上操作的执行者

`org.fisco.bcos.sdk.client.Client` 是日常调用最多的对象，提供：

### 查询类（不上链，不花 gas）

```java
// 获取当前区块高度
BlockNumber blockNumber = client.getBlockNumber();
long height = blockNumber.getBlockNumber().longValue();

// 获取指定高度的区块（false = 只返回交易哈希，不返回完整交易）
BcosBlock bcosBlock = client.getBlockByNumber(BigInteger.valueOf(13L), false);
BcosBlock.Block block = bcosBlock.getBlock();

// 获取交易详情
BcosTransaction tx = client.getTransactionByHash("0xabc...");

// 获取交易回执（交易已上链时）
BcosTransactionReceipt receipt = client.getTransactionReceipt("0xabc...");
```

### 发送交易（上链，通过合约 Java 包装类）

直接调 `Client` 发交易比较繁琐（需要手动编码 ABI），
实际项目里通过合约包装类（`EvidenceContract`）间接调用。

---

## 6. CryptoKeyPair：账户与签名

`org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair`

在 FISCO BCOS（以及以太坊）里，**账户 = 密钥对**：
- 私钥（private key）：签名交易，证明"是我发的"
- 公钥（public key）：由私钥推导出来
- 地址（address）：公钥哈希后取后 20 字节

```java
// 获取账户地址（类似以太坊地址，0x 开头的 40 位十六进制）
String address = keyPair.getAddress();  // 如 "0x9d6037dcc8b3..."

// 底层：当调用合约 addEvidence() 时，SDK 会自动用私钥签名交易
// 你不需要手动调用 sign()，合约包装类里的 executeTransaction() 会处理
```

### 国密 vs 标准的区别

| | 标准（ECDSA） | 国密（SM2） |
|--|--|--|
| 曲线 | secp256k1（比特币、以太坊同款） | SM2（国家标准，256位） |
| 哈希 | Keccak-256 | SM3 |
| 地址生成 | Keccak-256(pubkey)[12:] | SM3(pubkey)[12:] |
| Java 支持 | JDK 原生 | 需要 BouncyCastle |

Java 17 默认禁用了 secp256k1（该曲线被认为安全性有争议），
所以本项目**必须用 Java 11** 才能跑标准版，
或者像我们这样直接用国密版就没这个问题。

---

## 7. 国密（GM）：useSMCrypto 做了什么

设置 `useSMCrypto = true` 后，SDK 在以下环节都切换到国密算法：

```
TLS 握手 → SM2/SM4 加密（替代 ECDSA/AES）
    ↓
交易签名 → SM2 私钥签名（替代 secp256k1）
    ↓
哈希计算 → SM3（替代 Keccak-256）
    ↓
合约编译 → 不变（Solidity 语法相同）
    ↓
ABI 编码 → 不变（参数编码规则相同）
```

**Solidity 合约本身不需要改**，国密只影响底层的传输和签名层，
合约的 `mapping`、`require`、`emit` 这些逻辑完全一样。

---

## 8. EvidenceContract：合约 Java 包装类

文件：`src/main/java/com/evidence/contracts/EvidenceContract.java`

这个类是 Solidity 合约的 Java"翻译层"，通常由 `console` 工具自动生成，
也可以手动写（本项目是手动写的）。

### 继承关系

```
EvidenceContract
    └── extends Contract（来自 FISCO BCOS SDK）
            └── 内部持有 Client + CryptoKeyPair
            └── 提供 executeTransaction()、executeCallWithMultipleValueReturn() 等底层方法
```

### load vs deploy

```java
// load：合约已经部署在链上，只是拿到一个 Java 操作句柄
// 不发交易，不消耗 gas
EvidenceContract contract = EvidenceContract.load(contractAddress, client, keyPair);

// deploy：把合约字节码发到链上（只需要做一次）
// 发一笔特殊的"创建合约"交易
EvidenceContract contract = EvidenceContract.deploy(client, keyPair);
```

本项目合约已经部署好了（地址 `0xa0f88385...`），每次调用只用 `load()`。

### addEvidence 方法解析

```java
public TransactionReceipt addEvidence(
        String _fileHash, String _fileName, String _uploader,
        String _fileSize,   // 注意：fileSize 是 String，因为 Solidity uint256 超出 Java int 范围
        String _description) {

    // 1. 构造 ABI Function 对象，描述"我要调哪个函数、传什么参数"
    final Function function = new Function(
        FUNC_ADDEVIDENCE,                          // 函数名 "addEvidence"
        Arrays.asList(                             // 输入参数（按 ABI 类型包装）
            new Utf8String(_fileHash),             // string → Utf8String
            new Utf8String(_fileName),
            new Utf8String(_uploader),
            new Uint256(new BigInteger(_fileSize)), // uint256 → Uint256
            new Utf8String(_description)
        ),
        Collections.emptyList()                    // 输出参数（写操作通常不关心返回值）
    );

    // 2. 调用底层 executeTransaction()，SDK 自动完成：
    //    ABI 编码参数 → 构造交易 → SM2 签名 → 发送到节点 → 等待回执
    return executeTransaction(function);
}
```

### getEvidenceByHash 方法解析（查询，不上链）

```java
public List<Type> getEvidenceByHash(String _fileHash) throws ContractException {
    final Function function = new Function(
        FUNC_GETEVIDENCEBYHASH,
        Arrays.asList(new Utf8String(_fileHash)),  // 输入：1个string
        Arrays.asList(                              // 输出：6个返回值的类型声明
            new TypeReference<Utf8String>() {},     // fileHash
            new TypeReference<Utf8String>() {},     // fileName
            new TypeReference<Utf8String>() {},     // uploader
            new TypeReference<Uint256>() {},        // fileSize
            new TypeReference<Utf8String>() {},     // description
            new TypeReference<Uint256>() {}         // timestamp
        )
    );

    // executeCallWithMultipleValueReturn：只读调用，不发交易，不上链
    // 返回 List<Type>，需要手动 .getValue() 取出具体值
    return executeCallWithMultipleValueReturn(function);
}
```

---

## 9. ABI：Java 和 Solidity 之间的翻译协议

ABI（Application Binary Interface）定义了如何把 Java 参数编码成字节，
让 EVM 能识别和执行。

### ABI JSON 字符串

`EvidenceContract.ABI` 里存的是一个 JSON 字符串，描述了合约的所有函数签名：

```json
[
  {
    "constant": false,
    "inputs": [
      {"name": "_fileHash", "type": "string"},
      {"name": "_fileName", "type": "string"},
      {"name": "_uploader", "type": "string"},
      {"name": "_fileSize", "type": "uint256"},
      {"name": "_description", "type": "string"}
    ],
    "name": "addEvidence",
    "outputs": [{"name": "success", "type": "bool"}],
    "stateMutability": "nonpayable",
    "type": "function"
  }
]
```

- `constant: false` → 写操作，会修改链上状态，需要发交易
- `constant: true` / `stateMutability: "view"` → 只读，不上链
- `payable: false` → 调用时不能附带 ETH

### 类型映射

| Solidity 类型 | Java 类型 | SDK 包装类 |
|---|---|---|
| `string` | `String` | `Utf8String` |
| `uint256` | `BigInteger` | `Uint256` |
| `bool` | `Boolean` | `Bool` |
| `address` | `String`（0x开头） | `Address` |
| `bytes32` | `byte[32]` | `Bytes32` |
| `string[]` | `List<String>` | `DynamicArray<Utf8String>` |

---

## 10. TransactionReceipt：一笔交易的回执

`org.fisco.bcos.sdk.model.TransactionReceipt`

调用 `addEvidence()` 等写操作后，SDK 会等待交易被打包进区块，
然后返回 `TransactionReceipt`（交易回执）。

```java
TransactionReceipt receipt = contract.addEvidence(...);

// 交易哈希（唯一标识这笔交易）
String txHash = receipt.getTransactionHash();  // "0x5741fd94..."

// 区块号（这笔交易被打包进了哪个区块）
String blockNumber = receipt.getBlockNumber(); // "0xd"（十六进制字符串）
// 转换为长整型：
long blockNum = Long.parseLong(blockNumber.substring(2), 16); // 13

// 合约地址（部署合约时才有，调用函数时为空）
String contractAddr = receipt.getContractAddress();

// 交易状态（"0x0" = 成功，其他 = 失败）
String status = receipt.getStatus();

// 输出（函数有返回值时，ABI 解码后在这里）
String output = receipt.getOutput();

// 事件日志（合约里 emit Event 产生的日志）
List<TransactionReceipt.Logs> logs = receipt.getLogs();
```

**为什么 blockNumber 是十六进制字符串？**

FISCO BCOS（和以太坊一样）的 JSON-RPC 里所有数字都用十六进制表示，
SDK 直接把原始值透传出来，没有自动转换。
所以代码里需要 `Long.parseLong(s.substring(2), 16)` 手动转换。

---

## 11. BlockchainServiceImpl：业务层怎么调链

文件：`src/main/java/com/evidence/service/impl/BlockchainServiceImpl.java`

### addEvidence：核心上链方法

```java
public TransactionReceipt addEvidence(String fileHash, String fileName,
                                       String uploader, Long fileSize, String description) {
    // 每次调用都 load 一个合约实例
    // load() 很轻量，只是创建一个 Java 对象，不发网络请求
    EvidenceContract contract = EvidenceContract.load(contractAddress, client, cryptoKeyPair);

    // 调用合约方法，SDK 内部完成：
    //   1. ABI 编码参数
    //   2. 计算函数选择器（函数签名的 SM3 哈希前 4 字节）
    //   3. 构造 RawTransaction（nonce, gasPrice, gasLimit, to, data）
    //   4. SM2 私钥签名
    //   5. 发送到节点
    //   6. 等待打包，返回 receipt
    TransactionReceipt receipt = contract.addEvidence(
        fileHash, fileName, uploader, fileSize.toString(), description
    );

    return receipt;
}
```

### getBlockByNumber：查区块

```java
public String getBlockByNumber(Long blockNumber) {
    // 查询区块，false = 只返回交易哈希列表（不返回完整交易体，减少数据量）
    BcosBlock bcosBlock = client.getBlockByNumber(BigInteger.valueOf(blockNumber), false);
    BcosBlock.Block block = bcosBlock.getBlock();

    // FISCO BCOS 区块结构和以太坊基本一致：
    block.getHash()             // 区块哈希
    block.getParentHash()       // 父区块哈希（形成链）
    block.getNumber()           // 区块号（BigInteger）
    block.getTimestamp()        // 时间戳（十六进制字符串）
    block.getTransactionsRoot() // 所有交易的 Merkle 树根
    block.getStateRoot()        // 状态树根（账户状态快照）
    block.getGasLimit()         // 区块 gas 上限
    block.getGasUsed()          // 实际消耗的 gas
    block.getTransactions()     // 交易列表
}
```

### 交易列表的两种格式

```java
// getBlockByNumber(num, false) → 交易以哈希形式返回
for (Object txObj : block.getTransactions()) {
    if (txObj instanceof BcosBlock.TransactionHash) {
        String hash = ((BcosBlock.TransactionHash) txObj).get();
    }
}

// getBlockByNumber(num, true) → 交易以完整对象返回（数据量大）
if (txObj instanceof BcosBlock.TransactionObject) {
    String hash = ((BcosBlock.TransactionObject) txObj).getHash();
    String from = ((BcosBlock.TransactionObject) txObj).getFrom();
    // ...
}
```

---

## 12. AsyncTaskServiceImpl：异步上链架构

文件：`src/main/java/com/evidence/service/impl/AsyncTaskServiceImpl.java`

### 为什么要异步？

`addEvidence()` 需要等待交易被打包进区块，耗时约 **1~3 秒**。
如果用户上传文件时同步等待，HTTP 请求会超时，体验很差。

解决方案：文件上传成功后立即返回，上链操作放到后台异步执行。

```
用户上传文件
    │
    ▼
FileService.uploadFile()
    ├── 存 MySQL（立即完成）
    ├── 传 MinIO（几百毫秒）
    └── 推入 Redis 队列（1ms）← 就返回给用户了
            │
            │（后台异步）
            ▼
    AsyncTaskServiceImpl 的消费者线程
            │
            ▼
    blockchainService.addEvidence()（1~3秒）
            │
            ▼
    更新 MySQL chain_status = 1（上链成功）
```

### 四个消费者线程

服务启动时（`@PostConstruct`），用 `CompletableFuture.runAsync()` 启动 4 个后台线程，
各自消费不同的 Redis 队列：

```java
@PostConstruct
public void init() {
    running = true;
    // 四个线程各自独立运行，互不干扰
    CompletableFuture.runAsync(this::consumeBlockchainTasks);   // 上链队列
    CompletableFuture.runAsync(this::consumeFileProcessTasks);  // 文件处理队列
    CompletableFuture.runAsync(this::consumeStatsUpdateTasks);  // 统计更新队列
    CompletableFuture.runAsync(this::consumeDataSyncTasks);     // 数据同步队列
}
```

`CompletableFuture.runAsync()` 默认用 `ForkJoinPool.commonPool()` 线程池，
是 Java 8+ 的异步工具，不需要手动管理线程生命周期。

### 消费者主循环

```java
private void consumeBlockchainTasks() {
    while (running) {  // running 是 volatile boolean，停服时 @PreDestroy 设为 false
        try {
            // 从 Redis LIST 的头部弹出一个任务（LPOP）
            // 如果队列为空，返回 null
            RedisQueueUtil.BlockchainTask task = redisQueueUtil.popBlockchainTask();

            if (task != null) {
                processBlockchainTask(task);  // 处理任务
            } else {
                Thread.sleep(1000);  // 没任务时休眠 1 秒，避免空转 CPU
            }
        } catch (Exception e) {
            Thread.sleep(5000);  // 出错时休眠 5 秒再重试
        }
    }
}
```

### 指数退避重试

上链失败时（通常是 FISCO BCOS 还没初始化完），不立即重试，而是等待一段时间：

```java
// 重试次数 → 等待时间
// retryCount=0（第1次失败）→ 3^1 * 5 = 15秒？不对，看代码：
// Math.pow(3, retryCount) * 5
// retryCount=1（第1次失败后）→ 3^1 * 5 = 15s
// retryCount=2（第2次失败后）→ 3^2 * 5 = 45s

task.setRetryCount(task.getRetryCount() + 1);

if (task.getRetryCount() < 3) {
    long waitSeconds = (long) Math.pow(3, task.getRetryCount()) * 5;
    Thread.sleep(waitSeconds * 1000);
    redisQueueUtil.pushBlockchainTask(task);  // 重新推回队列
} else {
    // 3次都失败，标记 chain_status = 2（失败）
    evidenceService.update(...chainStatus = FAILED...);
}
```

**为什么叫指数退避（Exponential Backoff）？**

等待时间以指数增长（15s → 45s），而不是固定间隔重试。
这样如果节点暂时宕机，不会短时间内打出大量请求。
AWS、Google 的 SDK 都用这个策略。

---

## 13. Redis 队列：任务怎么传递

Redis 的 `LIST` 数据结构天然适合做任务队列：
- `RPUSH key value` → 向队列尾部加任务（生产者）
- `LPOP key` → 从队列头部取任务（消费者）

这就是一个 FIFO（先进先出）队列。

```java
// 生产者：文件上传完后推入队列
redisQueueUtil.pushBlockchainTask(new BlockchainTask(
    evidence.getId(), fileHash, fileName, uploader, fileSize, description
));

// 消费者：后台线程不断取任务
BlockchainTask task = redisQueueUtil.popBlockchainTask(); // LPOP，没有则返回 null
```

**任务对象的序列化**

任务对象存入 Redis 时，用 Jackson 序列化成 JSON 字符串。
Redis 里看到的大概是：

```json
["com.evidence.util.RedisQueueUtil$BlockchainTask",
 {"evidenceId":5,"fileHash":"351efe4b...","fileName":"Cover Letter.docx",
  "uploader":"admin","fileSize":12345,"description":"...","retryCount":0}]
```

开头的类名是因为配置了 `NON_FINAL` 类型信息，用于反序列化时恢复正确的类型。

---

## 14. @Lazy 注解：为什么要延迟初始化

Spring 默认在应用启动时初始化所有 Bean。
但 FISCO BCOS SDK 初始化需要：
1. 建立网络连接（节点必须先启动）
2. TLS 握手（证书验证）
3. 线程池启动

如果启动时节点没有准备好，整个 Spring 应用会启动失败。

加了 `@Lazy` 后：
- Spring 启动时**不**初始化这些 Bean
- 第一次有其他 Bean 注入 `Client` 或 `BcosSDK` 时才真正初始化
- 如果初始化失败，只有调用链路失败，不影响其他功能（如登录、文件上传）

```java
// BlockchainServiceImpl 里也加了 @Lazy：
@Lazy
@Autowired
private Client client;  // 只有第一次调用上链接口时才初始化

@Lazy
@Autowired
private CryptoKeyPair cryptoKeyPair;
```

这就是为什么系统刚启动时第一次上链可能失败（FISCO 刚好在初始化），
配合指数退避重试就能优雅处理这个竞态条件。

---

## 15. 完整上链流程时序图

```
用户                Spring Boot              Redis              FISCO BCOS
 │                      │                     │                     │
 │  POST /api/evidence   │                     │                     │
 │──────────────────────>│                     │                     │
 │                       │  存 MySQL（pending）│                     │
 │                       │───────────────────> │                     │
 │                       │  RPUSH 上链任务     │                     │
 │                       │──────────────────── │>                    │
 │  返回成功（立即）      │                     │                     │
 │<──────────────────────│                     │                     │
 │                       │                     │                     │
 │              [后台消费者线程]                 │                     │
 │                       │  LPOP 取任务        │                     │
 │                       │<──────────────────── │                     │
 │                       │                               addEvidence()│
 │                       │──────────────────────────────────────────>│
 │                       │                               SM2签名+上链 │
 │                       │                               打包进区块   │
 │                       │        TransactionReceipt                  │
 │                       │<──────────────────────────────────────────│
 │                       │  更新 MySQL（txHash, blockNum, status=1）  │
 │                       │───────────────────> │                     │
 │                       │                     │                     │
 │  用户刷新页面          │                     │                     │
 │──────────────────────>│                     │                     │
 │  chain_status=上链成功 │                     │                     │
 │<──────────────────────│                     │                     │
```

---

## 附：关键类速查

| 类 | 包 | 作用 |
|----|----|----|
| `BcosSDK` | `org.fisco.bcos.sdk` | SDK 入口，管理连接 |
| `Client` | `org.fisco.bcos.sdk.client` | 执行链上操作 |
| `CryptoKeyPair` | `org.fisco.bcos.sdk.crypto.keypair` | 账户密钥对 |
| `Contract` | `org.fisco.bcos.sdk.contract` | 合约包装类基类 |
| `TransactionReceipt` | `org.fisco.bcos.sdk.model` | 交易回执 |
| `Function` | `org.fisco.bcos.sdk.abi.datatypes` | ABI 函数描述 |
| `Utf8String` | `org.fisco.bcos.sdk.abi.datatypes` | Solidity string 的 Java 表示 |
| `Uint256` | `org.fisco.bcos.sdk.abi.datatypes.generated` | Solidity uint256 的 Java 表示 |
| `BlockNumber` | `org.fisco.bcos.sdk.client.protocol.response` | getBlockNumber() 的返回值 |
| `BcosBlock` | `org.fisco.bcos.sdk.client.protocol.response` | getBlockByNumber() 的返回值 |
