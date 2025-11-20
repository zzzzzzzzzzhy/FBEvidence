#!/bin/bash

echo "=== 启动区块链存证系统所有服务 ==="

# 1. 启动FISCO BCOS节点
echo "1. 启动FISCO BCOS节点..."
cd /data/fisco/nodes/127.0.0.1
bash start_all.sh
sleep 2

# 2. 启动MinIO
echo "2. 启动MinIO服务..."
if pgrep -f "minio server" > /dev/null; then
    echo "   MinIO已在运行"
else
    nohup minio server /data/minio --console-address ":9001" > /data/minio.log 2>&1 &
    sleep 3
    echo "   MinIO启动完成"
fi

# 3. 检查MySQL
echo "3. 检查MySQL服务..."
if systemctl is-active --quiet mysql; then
    echo "   MySQL正在运行"
else
    echo "   启动MySQL..."
    sudo systemctl start mysql
fi

echo ""
echo "=== 服务状态 ==="
echo "FISCO BCOS节点数: $(ps -ef | grep fisco-bcos | grep -v grep | wc -l)"
echo "MinIO状态: $(pgrep -f 'minio server' > /dev/null && echo '运行中' || echo '未运行')"
echo "MySQL状态: $(systemctl is-active mysql)"
echo ""
echo "=== 访问地址 ==="
echo "MinIO控制台: http://127.0.0.1:9001 (minioadmin/minioadmin)"
echo "应用地址: http://localhost:8080 (admin/123123)"
echo ""
echo "现在可以启动应用了:"
echo "cd /data/FBEvidence/FiscoBcosEvidence/untitled && mvn spring-boot:run"
