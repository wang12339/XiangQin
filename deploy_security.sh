#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# 乡亲 · VPS 安全配置部署脚本
# 运行方式: bash deploy_security.sh
# ═══════════════════════════════════════════════════════════════

set -e

VPS="root@43.99.13.12"
REMOTE_CONF="/etc/nginx/conf.d/xiangqin-relay.conf"
BACKUP_CONF="/etc/nginx/conf.d/xiangqin-relay.conf.bak.$(date +%Y%m%d%H%M%S)"

echo "🔧 步骤 1: 备份旧配置..."
ssh "$VPS" "cp $REMOTE_CONF $BACKUP_CONF 2>/dev/null || true"

echo "🔧 步骤 2: 上传安全配置..."
scp nginx_security.conf "$VPS:/tmp/xiangqin-relay.conf"

echo "🔧 步骤 3: 部署配置..."
ssh "$VPS" "cp /tmp/xiangqin-relay.conf $REMOTE_CONF && nginx -t && systemctl reload nginx"

echo "✅ 安全配置部署完成！"
echo "   备份: $VPS:$BACKUP_CONF"
