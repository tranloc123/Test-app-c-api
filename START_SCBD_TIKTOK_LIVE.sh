#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"

if ! command -v node >/dev/null 2>&1; then
  echo "[SCBD] Chưa có Node.js. Đang cài..."
  pkg install nodejs -y
fi

if ! node -e "require('ws')" >/dev/null 2>&1; then
  echo "[SCBD] Đang cài module WebSocket 'ws'..."
  npm install --omit=dev
fi

echo ""
echo "============================================================"
echo " SCBD TikTok LIVE V0.6.0R1 - REAL INTEGRATION"
echo " Control: http://127.0.0.1:8797/"
echo " Native APK UDP: 127.0.0.1:8796"
echo " PPSSPP Debugger: ws://127.0.0.1:9000/debugger"
echo "============================================================"
echo ""

exec node scbd_tiktok_live_v060.js
