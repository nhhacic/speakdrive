#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SpeakDrive / English Speaking App - Background Sync Receiver (Windows)
======================================================================
Lắng nghe thông báo đồng bộ từ máy Linux qua mạng Tailscale.
Khi máy Linux phát sinh commit mới hoặc phiên làm việc AI mới (kể cả khi
người dùng thao tác remote từ điện thoại), Linux sẽ gửi HTTP notify tới đây.
Receiver này sẽ lập tức kéo (pull) Code và Phiên làm việc về máy Windows.
"""

import os
import sys

# Prevent recursive hooks
os.environ["SPEAKDRIVE_SYNCING"] = "1"

import sys
import os
import traceback

CRASH_LOG = os.path.join(os.environ.get("TEMP", "/tmp"), "speakdrive_sync", "crash.log")
try:
    os.makedirs(os.path.dirname(CRASH_LOG), exist_ok=True)
    if sys.stdout is None:
        sys.stdout = open(os.devnull, 'w', encoding='utf-8')
    if sys.stderr is None:
        sys.stderr = open(CRASH_LOG, 'a', encoding='utf-8')
except Exception as e:
    pass

import json
import time
import socket
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path

# Cấu hình kết nối
LISTEN_PORT = 49200
SYNC_SECRET_TOKEN = "speakdrive_sync_token_secure_49200"

LOG_DIR = Path(os.environ.get("TEMP", "/tmp")) / "speakdrive_sync"
LOG_DIR.mkdir(parents=True, exist_ok=True)
LOG_FILE = LOG_DIR / "receiver.log"


def log(msg):
    ts = time.strftime("%Y-%m-%d %H:%M:%S")
    formatted = f"[{ts}] {msg}"
    try:
        if sys.stdout:
            print(formatted, flush=True)
    except Exception:
        pass
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(formatted + "\n")
    except Exception:
        pass


def execute_sync_from_linux():
    """Chạy quy trình kéo dữ liệu từ Linux về Windows trong một luồng riêng biệt"""
    log("🔄 Bắt đầu kéo dữ liệu từ Linux về Windows...")
    try:
        project_dir = Path(__file__).resolve().parent.parent
        script_path = project_dir / "scripts" / "sync_workspace.py"
        
        # Import trực tiếp hàm đồng bộ để thực thi siêu tốc
        sys.path.insert(0, str(project_dir))
        from scripts.sync_workspace import sync_code_git, sync_conversations_from_linux
        
        # 1. Kéo code git
        sync_code_git("from-linux")
        
        # 2. Kéo phiên làm việc gia tăng
        sync_conversations_from_linux()
        
        log("🎉 Hoàn tất đồng bộ dữ liệu từ Linux về Windows thành công!")
    except Exception as e:
        log(f"❌ Lỗi khi đồng bộ từ Linux: {e}")
        import traceback
        traceback.print_exc()


class SyncHTTPHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Không in access log mặc định ra console để tránh rác
        pass

    def do_GET(self):
        if self.path == "/ping":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            resp = {
                "status": "online",
                "machine": "windows",
                "project": "speakdrive",
                "time": time.time()
            }
            self.wfile.write(json.dumps(resp).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/notify-sync":
            auth_token = self.headers.get("X-Sync-Token")
            if auth_token != SYNC_SECRET_TOKEN:
                self.send_response(403)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"error": "Forbidden"}).encode("utf-8"))
                log("⚠️ Nhận request notify-sync nhưng sai Token xác thực!")
                return

            # Đọc payload nếu có
            content_len = int(self.headers.get("Content-Length", 0))
            payload = {}
            if content_len > 0:
                try:
                    payload = json.loads(self.rfile.read(content_len).decode("utf-8"))
                except Exception:
                    pass

            source = payload.get("source", "linux-trigger")
            log(f"📩 Nhận thông báo đồng bộ từ Linux (Nguồn: {source})")

            # Trả về 200 ngay lập tức để phía Linux không phải chờ
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "sync_started"}).encode("utf-8"))

            # Kích hoạt đồng bộ trong thread riêng
            t = threading.Thread(target=execute_sync_from_linux, daemon=True)
            t.start()
        else:
            self.send_response(404)
            self.end_headers()


def is_port_in_use(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        return s.connect_ex(('127.0.0.1', port)) == 0


def main():
    if is_port_in_use(LISTEN_PORT):
        log(f"⚠️ Cổng {LISTEN_PORT} đã có tiến trình khác lắng nghe. Receiver đang hoạt động sẵn rồi.")
        return

    server_address = ('0.0.0.0', LISTEN_PORT)
    httpd = HTTPServer(server_address, SyncHTTPHandler)
    log(f"🚀 SpeakDrive Sync Receiver đang chạy ngầm tại cổng {LISTEN_PORT}...")
    log(f"   Log ghi tại: {LOG_FILE}")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        log("Receiver đã dừng theo yêu cầu người dùng.")
    finally:
        httpd.server_close()


if __name__ == "__main__":
    main()
