#!/bin/bash
echo "启动区块链存证系统..."
echo "设置JVM字符编码参数..."

export JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dspring.profiles.active=dev"

java $JAVA_OPTS -jar target/blockchain-evidence-system-1.0.0.jar