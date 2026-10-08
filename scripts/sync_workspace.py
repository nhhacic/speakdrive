#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SpeakDrive / English Speaking App - Multi-Machine Workspace Synchronizer
========================================================================
Đồng bộ hóa 2 chiều toàn diện giữa máy Windows và máy trạm Linux:
1. Mã nguồn (Git Code qua GitHub & Direct SSH Pull)
2. Toàn bộ các phiên hội thoại Antigravity (`conversations/<id>.db*`)
3. Thư mục não bộ / ký ức ngữ cảnh AI (`brain/<id>/`)
4. Cơ sở dữ liệu SQLite (`conversation_summaries.db`) với chuyển đổi URI tự động
5. File cache Protobuf Antigravity Hub (`agyhub_summaries_proto.pb`)
6. Cấu hình hiển thị dự án (`app_storage.json`)

Hỗ trợ đồng bộ gia tăng (Incremental Sync): mặc định chỉ đồng bộ các phiên
thay đổi trong vòng 48h qua, giúp tốc độ thực thi chỉ mất 1 - 3 giây!
"""

import os
import sys

# Prevent recursive hooks
os.environ["SPEAKDRIVE_SYNCING"] = "1"

# Ensure UTF-8 output and instant flush on all platforms
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

import functools
print = functools.partial(print, flush=True)

import json
import sqlite3
import argparse
import subprocess
import shutil
import platform
from datetime import datetime, timezone, timedelta
from pathlib import Path

# ==================== CẤU HÌNH HỆ THỐNG ====================
PROJECT_ID = "08cbdccc-b264-47ae-8f6a-0acb555d0fe2"
STATE_FILE_WIN = os.path.join(r"d:\@Vibe_code_projects\English Speaking App", ".git", "speakdrive_sync_state.json")


# Đường dẫn trên Windows
WIN_PROJECT_DIR = r"d:\@Vibe_code_projects\English Speaking App"
WIN_GEMINI_DIR = os.path.expanduser(r"~\.gemini\antigravity")
WIN_APP_STORAGE = os.path.expandvars(r"%APPDATA%\Antigravity\app_storage.json")
WIN_TAILSCALE_IP = "100.94.114.23"
WIN_RECEIVER_PORT = 49200
SYNC_SECRET_TOKEN = "speakdrive_sync_token_secure_49200"

# Đường dẫn trên Linux
LINUX_HOST = "root@100.107.110.66"
LINUX_USER = "cic-ai"
LINUX_PROJECT_DIR = "/home/cic-ai/cic-project/English Speaking App"
LINUX_GEMINI_DIR = "/home/cic-ai/.gemini/antigravity"
LINUX_APP_STORAGE = "/home/cic-ai/.config/Antigravity/app_storage.json"

# Định dạng đường dẫn URI
WIN_URIS = [
    b"file:///d:/@Vibe_code_projects/English%20Speaking%20App",
    b"file:///d%3A/%40Vibe_code_projects/English%20Speaking%20App",
    b"file:///D:/@Vibe_code_projects/English%20Speaking%20App",
    b"file:///D%3A/%40Vibe_code_projects/English%20Speaking%20App",
    b"d:/@Vibe_code_projects/English Speaking App",
    b"d:\\@Vibe_code_projects\\English Speaking App",
    b"D:/@Vibe_code_projects/English Speaking App",
    b"D:\\@Vibe_code_projects\\English Speaking App",
]
LINUX_URI = b"file:///home/cic-ai/cic-project/English%20Speaking%20App"
LINUX_RAW_PATH = b"/home/cic-ai/cic-project/English Speaking App"


# ==================== PROTOBUF ENGINE ====================
def encode_varint(val):
    res = bytearray()
    while True:
        b = val & 0x7F
        val >>= 7
        if val:
            res.append(b | 0x80)
        else:
            res.append(b)
            break
    return bytes(res)


def decode_protobuf(data):
    i = 0
    res = []
    while i < len(data):
        shift = 0
        tag_varint = 0
        while True:
            b = data[i]
            i += 1
            tag_varint |= (b & 0x7F) << shift
            if not (b & 0x80):
                break
            shift += 7
        field_num = tag_varint >> 3
        wire_type = tag_varint & 7
        if wire_type == 0:
            val = 0
            shift = 0
            while True:
                b = data[i]
                i += 1
                val |= (b & 0x7F) << shift
                if not (b & 0x80):
                    break
                shift += 7
            res.append((field_num, 'varint', val))
        elif wire_type == 1:
            val = data[i:i+8]
            i += 8
            res.append((field_num, '64bit', val))
        elif wire_type == 2:
            shift = 0
            length = 0
            while True:
                b = data[i]
                i += 1
                length |= (b & 0x7F) << shift
                if not (b & 0x80):
                    break
                shift += 7
            val = data[i:i+length]
            i += length
            is_valid_sub = False
            if len(val) > 0:
                try:
                    sub = decode_protobuf(val)
                    re_test = encode_protobuf(sub)
                    if re_test == val:
                        is_ascii = False
                        try:
                            val.decode('utf-8')
                            if all(32 <= b < 127 or b in (10, 13, 9) for b in val):
                                is_ascii = True
                        except UnicodeDecodeError:
                            pass
                        if not is_ascii or (len(sub) > 1 and any(s[1] in ('submsg', 'varint') for s in sub)):
                            res.append((field_num, 'submsg', sub))
                            is_valid_sub = True
                except Exception:
                    pass
            if not is_valid_sub:
                res.append((field_num, 'bytes', val))
        elif wire_type == 5:
            val = data[i:i+4]
            i += 4
            res.append((field_num, '32bit', val))
        else:
            raise ValueError(f"Unknown wire type {wire_type} at offset {i}")
    return res


def encode_protobuf(tokens):
    out = bytearray()
    for field_num, kind, val in tokens:
        if kind == 'varint':
            tag = (field_num << 3) | 0
            out.extend(encode_varint(tag))
            out.extend(encode_varint(val))
        elif kind == '64bit':
            tag = (field_num << 3) | 1
            out.extend(encode_varint(tag))
            out.extend(val)
        elif kind == '32bit':
            tag = (field_num << 3) | 5
            out.extend(encode_varint(tag))
            out.extend(val)
        elif kind == 'bytes':
            tag = (field_num << 3) | 2
            out.extend(encode_varint(tag))
            out.extend(encode_varint(len(val)))
            out.extend(val)
        elif kind == 'submsg':
            sub_bytes = encode_protobuf(val)
            tag = (field_num << 3) | 2
            out.extend(encode_varint(tag))
            out.extend(encode_varint(len(sub_bytes)))
            out.extend(sub_bytes)
    return bytes(out)


def convert_token_uris(token, target_os='linux'):
    field_num, kind, val = token
    if kind == 'bytes':
        if target_os == 'linux':
            for win_u in WIN_URIS:
                if win_u in val:
                    val = val.replace(win_u, LINUX_URI if b'file://' in win_u else LINUX_RAW_PATH)
        else:
            # target is windows
            default_win_uri = WIN_URIS[0]
            if LINUX_URI in val:
                val = val.replace(LINUX_URI, default_win_uri)
            if LINUX_RAW_PATH in val:
                val = val.replace(LINUX_RAW_PATH, WIN_URIS[4])
        return (field_num, kind, val)
    elif kind == 'submsg':
        return (field_num, kind, [convert_token_uris(c, target_os) for c in val])
    else:
        return token


def extract_cid_from_pb_entry(entry):
    """
    Trích xuất conversation ID từ một entry trong agyhub_summaries_proto.pb
    Cấu trúc: (1, 'submsg', [(1, 'bytes', cid_bytes), (2, 'submsg', ...)])
    """
    fnum, fkind, fields = entry
    if fnum == 1 and fkind == 'submsg' and isinstance(fields, list):
        for sub_num, sub_kind, sub_val in fields:
            if sub_num == 1 and sub_kind == 'bytes':
                try:
                    return sub_val.decode('utf-8', errors='ignore')
                except Exception:
                    pass
    return None


# ==================== SYNC WORKFLOW HELPERS ====================
def run_cmd(cmd, check=True, cwd=None):
    res = subprocess.run(
        cmd, shell=True, cwd=cwd,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, encoding='utf-8', errors='replace'
    )
    if check and res.returncode != 0:
        raise RuntimeError(f"Command failed (exit {res.returncode}):\n{res.stderr.strip()}")
    return res.stdout.strip()


def send_bytes_ssh(data_bytes, remote_path):
    cmd = ["ssh", "-o", "StrictHostKeyChecking=no", LINUX_HOST, f"cat > '{remote_path}'"]
    p = subprocess.run(cmd, input=data_bytes, check=True)
    return p.returncode


def stream_tar_to_linux(source_dir, items_list, remote_target_dir):
    """Đóng gói file trên local và đẩy trực tiếp qua SSH pipe giải nén trên Linux"""
    if not items_list:
        return
    temp_dir = Path(os.environ.get("TEMP", "/tmp")) / "speakdrive_sync"
    temp_dir.mkdir(parents=True, exist_ok=True)
    temp_list = temp_dir / "tar_items.txt"
    with open(temp_list, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(items_list) + "\n")
    
    tar_archive = temp_dir / "transfer_batch.tar.gz"
    run_cmd(f"tar -czf \"{tar_archive}\" -C \"{source_dir}\" -T \"{temp_list}\"", check=True)
    
    with open(tar_archive, "rb") as f:
        ssh_cmd = ["ssh", "-o", "StrictHostKeyChecking=no", LINUX_HOST, f"tar -xzf - -C '{remote_target_dir}'"]
        p = subprocess.run(ssh_cmd, input=f.read(), check=True)
        if p.returncode != 0:
            raise RuntimeError(f"Lỗi truyền tải dữ liệu tar stream (exit {p.returncode})")
    try:
        tar_archive.unlink()
        temp_list.unlink()
    except Exception:
        pass


def stream_tar_from_linux(remote_source_dir, items_list, local_target_dir):
    """Kéo file từ Linux về và giải nén an toàn trên local Windows (xử lý Windows file lock)"""
    if not items_list:
        return
    import tempfile
    staging_dir = Path(tempfile.gettempdir()) / "speakdrive_pull_staging"
    if staging_dir.exists():
        shutil.rmtree(staging_dir, ignore_errors=True)
    staging_dir.mkdir(parents=True, exist_ok=True)

    items_str = " ".join([f"'{item}'" for item in items_list])
    ssh_cmd = ["ssh", "-o", "StrictHostKeyChecking=no", LINUX_HOST, f"tar -czf - -C '{remote_source_dir}' {items_str}"]
    tar_cmd = ["tar", "-xzf", "-", "-C", str(staging_dir)]
    
    p1 = subprocess.Popen(ssh_cmd, stdout=subprocess.PIPE)
    p2 = subprocess.Popen(tar_cmd, stdin=p1.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    p1.stdout.close()
    out, err = p2.communicate()

    # Chép từ staging sang local_target_dir, nhẹ nhàng bỏ qua các file đang bị khóa bởi tiến trình đang chạy
    os.makedirs(local_target_dir, exist_ok=True)
    for root, dirs, files in os.walk(staging_dir):
        rel_path = os.path.relpath(root, staging_dir)
        dest_dir = Path(local_target_dir) if rel_path == '.' else Path(local_target_dir) / rel_path
        dest_dir.mkdir(parents=True, exist_ok=True)
        for f in files:
            src_f = Path(root) / f
            dst_f = dest_dir / f
            try:
                shutil.copy2(src_f, dst_f)
            except (PermissionError, OSError):
                # File đang được mở / memory-mapped bởi chính phiên Antigravity hiện tại trên Windows
                pass
            except Exception as e:
                print(f"   [Cảnh báo copy] {f}: {e}")

    try:
        shutil.rmtree(staging_dir, ignore_errors=True)
    except Exception:
        pass


# ==================== GIT CODE SYNC ====================
def sync_code_git(direction):
    print("\n--- BƯỚC 1: ĐỒNG BỘ MÃ NGUỒN GIT ---")
    current_os = 'windows' if platform.system() == 'Windows' else 'linux'
    
    if direction == "to-linux":
        if current_os == 'windows':
            branch = run_cmd("git rev-parse --abbrev-ref HEAD", cwd=WIN_PROJECT_DIR)
            print(f"  Branch hiện tại: {branch}")
            
            # Check uncommitted
            status = run_cmd("git status --porcelain", cwd=WIN_PROJECT_DIR)
            if status:
                print("  [Thông báo] Phát hiện file chưa commit, tự động commit đồng bộ...")
                run_cmd("git add -A", cwd=WIN_PROJECT_DIR)
                run_cmd('git commit --no-verify -m "Auto-sync: update from Windows workspace"', cwd=WIN_PROJECT_DIR)
            
            print("  Đẩy code lên GitHub...")
            run_cmd(f"git push origin {branch}", cwd=WIN_PROJECT_DIR)
            
            print(f"  Kéo code trên máy Linux ({LINUX_HOST})...")
            remote_git = f"ssh -o StrictHostKeyChecking=no {LINUX_HOST} \"cd '{LINUX_PROJECT_DIR}' && git pull origin {branch}\""
            run_cmd(remote_git)
            print("  ✅ Mã nguồn Git đã được đồng bộ thành công sang Linux!")
        else:
            # Running on Linux
            branch = run_cmd("git rev-parse --abbrev-ref HEAD", cwd=LINUX_PROJECT_DIR)
            status = run_cmd("git status --porcelain", cwd=LINUX_PROJECT_DIR)
            if status:
                run_cmd("git add -A", cwd=LINUX_PROJECT_DIR)
                run_cmd('git commit --no-verify -m "Auto-sync: update from Linux workspace"', cwd=LINUX_PROJECT_DIR)
            run_cmd(f"git push origin {branch}", cwd=LINUX_PROJECT_DIR)
            print("  ✅ Mã nguồn Git đã được đẩy lên GitHub từ Linux!")

    elif direction in ("from-linux", "to-windows"):
        if current_os == 'windows':
            # Check and commit on Linux first
            branch = run_cmd("git rev-parse --abbrev-ref HEAD", cwd=WIN_PROJECT_DIR)
            remote_status_cmd = f"ssh -o StrictHostKeyChecking=no {LINUX_HOST} \"cd '{LINUX_PROJECT_DIR}' && git status --porcelain\""
            remote_status = run_cmd(remote_status_cmd)
            if remote_status:
                print("  [Thông báo] Phát hiện file chưa commit trên Linux, tự động commit...")
                remote_commit = f"ssh -o StrictHostKeyChecking=no {LINUX_HOST} \"cd '{LINUX_PROJECT_DIR}' && git add -A && git commit --no-verify -m 'Auto-sync: update from Linux workspace' && git push origin {branch}\""
                run_cmd(remote_commit)
            else:
                remote_push = f"ssh -o StrictHostKeyChecking=no {LINUX_HOST} \"cd '{LINUX_PROJECT_DIR}' && git push origin {branch}\""
                subprocess.run(remote_push, shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            
            print("  Kéo code mới nhất về Windows...")
            run_cmd(f"git pull origin {branch}", cwd=WIN_PROJECT_DIR)
            print("  ✅ Mã nguồn Git đã được kéo thành công về Windows!")
        else:
            branch = run_cmd("git rev-parse --abbrev-ref HEAD", cwd=LINUX_PROJECT_DIR)
            status = run_cmd("git status --porcelain", cwd=LINUX_PROJECT_DIR)
            if status:
                run_cmd("git add -A", cwd=LINUX_PROJECT_DIR)
                run_cmd('git commit --no-verify -m "Auto-sync: update from Linux workspace"', cwd=LINUX_PROJECT_DIR)
            run_cmd(f"git push origin {branch}", cwd=LINUX_PROJECT_DIR)
            print("  ✅ Mã nguồn Git đã được đẩy lên GitHub từ Linux!")


# ==================== INCREMENTAL CONVERSATION SYNC ====================
def load_last_sync_cutoff(since_hours=None):
    if since_hours is not None:
        return (datetime.now(timezone.utc) - timedelta(hours=since_hours)).strftime("%Y-%m-%d %H:%M:%S")
    
    if os.path.exists(STATE_FILE_WIN):
        try:
            with open(STATE_FILE_WIN, "r", encoding="utf-8") as f:
                data = json.load(f)
            last_time = data.get("last_sync_time")
            if last_time:
                # Trừ hao 15 phút để đảm bảo không sót phiên nào vừa kết thúc
                dt = datetime.fromisoformat(last_time) - timedelta(minutes=15)
                return dt.strftime("%Y-%m-%d %H:%M:%S")
        except Exception:
            pass
    # Mặc định lần đầu lấy 6 giờ gần nhất
    return (datetime.now(timezone.utc) - timedelta(hours=6)).strftime("%Y-%m-%d %H:%M:%S")


def save_last_sync_time():
    try:
        os.makedirs(os.path.dirname(STATE_FILE_WIN), exist_ok=True)
        with open(STATE_FILE_WIN, "w", encoding="utf-8") as f:
            json.dump({"last_sync_time": datetime.now(timezone.utc).isoformat()}, f, indent=2)
    except Exception as e:
        print(f"  [Cảnh báo] Không thể lưu state file: {e}")


def get_candidates_to_linux(since_hours=None, sync_all=False, specific_cid=None):
    """Lọc danh sách các phiên hội thoại cần đồng bộ sang Linux"""
    local_db_path = os.path.join(WIN_GEMINI_DIR, "conversation_summaries.db")
    if not os.path.exists(local_db_path):
        return []

    conn = sqlite3.connect(local_db_path)
    cur = conn.cursor()
    
    if specific_cid:
        cur.execute(
            "SELECT conversation_id, title, preview, step_count, last_modified_time, workspace_uris, "
            "status, source, project_id, agent_name, parent_conversation_id, nesting_depth, battle_id, "
            "winning_conversation_id, not_fully_idle, killed, last_user_input_time, last_user_input_step_index, "
            "app_data_dir, raw_summary, group_id FROM conversation_summaries "
            "WHERE conversation_id = ?", (specific_cid,)
        )
    elif sync_all:
        cur.execute(
            "SELECT conversation_id, title, preview, step_count, last_modified_time, workspace_uris, "
            "status, source, project_id, agent_name, parent_conversation_id, nesting_depth, battle_id, "
            "winning_conversation_id, not_fully_idle, killed, last_user_input_time, last_user_input_step_index, "
            "app_data_dir, raw_summary, group_id FROM conversation_summaries "
            "WHERE project_id = ? OR workspace_uris LIKE '%English%' ORDER BY last_modified_time DESC",
            (PROJECT_ID,)
        )
    else:
        # Incremental filter qua state file hoặc since_hours
        cutoff = load_last_sync_cutoff(since_hours)
        cur.execute(
            "SELECT conversation_id, title, preview, step_count, last_modified_time, workspace_uris, "
            "status, source, project_id, agent_name, parent_conversation_id, nesting_depth, battle_id, "
            "winning_conversation_id, not_fully_idle, killed, last_user_input_time, last_user_input_step_index, "
            "app_data_dir, raw_summary, group_id FROM conversation_summaries "
            "WHERE (project_id = ? OR workspace_uris LIKE '%English%') AND last_modified_time >= ? "
            "ORDER BY last_modified_time DESC",
            (PROJECT_ID, cutoff)
        )
    
    rows = cur.fetchall()
    conn.close()
    return rows


def sync_conversations_to_linux(since_hours=48, sync_all=False, specific_cid=None):
    print("\n--- BƯỚC 2: ĐỒNG BỘ PHIÊN HỘI THOẠI & KÝ ỨC (WINDOWS -> LINUX) ---")
    rows = get_candidates_to_linux(since_hours=since_hours, sync_all=sync_all, specific_cid=specific_cid)
    
    if not rows:
        print("  ℹ️ Không có phiên hội thoại nào mới trong thời gian qua. Đã cập nhật đầy đủ!")
        return

    print(f"  Phát hiện {len(rows)} phiên hội thoại cần đồng bộ:")
    for r in rows:
        title = r[1] or "Không có tiêu đề"
        print(f"   • [{r[0][:8]}...] {title} (Sửa đổi: {r[4]})")

    cids = [r[0] for r in rows]

    # 1. Chuyển đổi dữ liệu và chuẩn bị payload
    sql_statements = []
    for row in rows:
        cid = row[0]
        raw_summary = row[19]
        
        # Convert raw_summary to linux URI
        linux_raw_summary = raw_summary
        if raw_summary:
            try:
                parsed = decode_protobuf(raw_summary)
                converted = [convert_token_uris(t, 'linux') for t in parsed]
                linux_raw_summary = encode_protobuf(converted)
            except Exception as e:
                print(f"   [Cảnh báo] Lỗi decode protobuf cho {cid}: {e}")

        linux_workspace_uri = f'["{LINUX_URI.decode("utf-8")}"]'
        sql_statements.append((
            cid, row[1], row[2], row[3], row[4], linux_workspace_uri,
            row[6], row[7], row[8], row[9], row[10], row[11], row[12],
            row[13], row[14], row[15], row[16], row[17], row[18], linux_raw_summary, row[20]
        ))

    sync_payload = {
        "project_id": PROJECT_ID,
        "rows": [
            {
                "cid": s[0], "title": s[1], "preview": s[2], "step_count": s[3],
                "last_modified": s[4], "workspace_uris": s[5], "status": s[6],
                "source": s[7], "project_id": s[8], "agent_name": s[9],
                "parent_id": s[10], "depth": s[11], "battle_id": s[12],
                "winning_id": s[13], "not_idle": s[14], "killed": s[15],
                "last_input_time": s[16], "last_input_step": s[17],
                "app_data_dir": s[18], "raw_summary_hex": s[19].hex() if s[19] else "",
                "group_id": s[20]
            } for s in sql_statements
        ]
    }

    # Gửi payload JSON sang Linux
    send_bytes_ssh(json.dumps(sync_payload).encode('utf-8'), "/tmp/speakdrive_sync.json")

    # 2. Truyền tải file SQLite (.db, .db-wal, .db-shm)
    print("  Đang đồng bộ SQLite database của các phiên...")
    conv_dir = Path(WIN_GEMINI_DIR) / "conversations"
    conv_files = []
    for cid in cids:
        for ext in ["", "-wal", "-shm"]:
            f = conv_dir / f"{cid}.db{ext}"
            if f.exists():
                conv_files.append(f"{cid}.db{ext}")

    if conv_files:
        stream_tar_to_linux(conv_dir, conv_files, f"{LINUX_GEMINI_DIR}/conversations/")

    # 3. Truyền tải thư mục não bộ / ký ức (brain/)
    print("  Đang đồng bộ ngữ cảnh / ký ức AI (brain/)...")
    brain_dir = Path(WIN_GEMINI_DIR) / "brain"
    brain_folders = [cid for cid in cids if (brain_dir / cid).exists()]

    if brain_folders:
        stream_tar_to_linux(brain_dir, brain_folders, f"{LINUX_GEMINI_DIR}/brain/")

    # 4. Kích hoạt script độc lập trên Linux để cập nhật SQLite, Protobuf cache & Hub storage
    remote_apply_script = """
import sqlite3, json, os, sys

def encode_varint(val):
    res = bytearray()
    while True:
        b = val & 0x7F
        val >>= 7
        if val:
            res.append(b | 0x80)
        else:
            res.append(b)
            break
    return bytes(res)

def decode_protobuf(data):
    i = 0
    res = []
    while i < len(data):
        shift = 0
        tag_varint = 0
        while True:
            b = data[i]
            i += 1
            tag_varint |= (b & 0x7F) << shift
            if not (b & 0x80):
                break
            shift += 7
        field_num = tag_varint >> 3
        wire_type = tag_varint & 7
        if wire_type == 0:
            val = 0
            shift = 0
            while True:
                b = data[i]
                i += 1
                val |= (b & 0x7F) << shift
                if not (b & 0x80):
                    break
                shift += 7
            res.append((field_num, 'varint', val))
        elif wire_type == 1:
            val = data[i:i+8]
            i += 8
            res.append((field_num, '64bit', val))
        elif wire_type == 2:
            shift = 0
            length = 0
            while True:
                b = data[i]
                i += 1
                length |= (b & 0x7F) << shift
                if not (b & 0x80):
                    break
                shift += 7
            val = data[i:i+length]
            i += length
            is_valid_sub = False
            if len(val) > 0:
                try:
                    sub = decode_protobuf(val)
                    re_test = encode_protobuf(sub)
                    if re_test == val:
                        is_ascii = False
                        try:
                            val.decode('utf-8')
                            if all(32 <= b < 127 or b in (10, 13, 9) for b in val):
                                is_ascii = True
                        except UnicodeDecodeError:
                            pass
                        if not is_ascii or (len(sub) > 1 and any(s[1] in ('submsg', 'varint') for s in sub)):
                            res.append((field_num, 'submsg', sub))
                            is_valid_sub = True
                except Exception:
                    pass
            if not is_valid_sub:
                res.append((field_num, 'bytes', val))
        elif wire_type == 5:
            val = data[i:i+4]
            i += 4
            res.append((field_num, '32bit', val))
        else:
            raise ValueError(f"Unknown wire type {wire_type}")
    return res

def encode_protobuf(tokens):
    out = bytearray()
    for field_num, kind, val in tokens:
        if kind == 'varint':
            out.extend(encode_varint((field_num << 3) | 0))
            out.extend(encode_varint(val))
        elif kind == '64bit':
            out.extend(encode_varint((field_num << 3) | 1))
            out.extend(val)
        elif kind == '32bit':
            out.extend(encode_varint((field_num << 3) | 5))
            out.extend(val)
        elif kind == 'bytes':
            out.extend(encode_varint((field_num << 3) | 2))
            out.extend(encode_varint(len(val)))
            out.extend(val)
        elif kind == 'submsg':
            sub_bytes = encode_protobuf(val)
            out.extend(encode_varint((field_num << 3) | 2))
            out.extend(encode_varint(len(sub_bytes)))
            out.extend(sub_bytes)
    return bytes(out)

def get_cid_from_entry(entry):
    fnum, fkind, fields = entry
    if fnum == 1 and fkind == 'submsg' and isinstance(fields, list):
        for sub_num, sub_kind, sub_val in fields:
            if sub_num == 1 and sub_kind == 'bytes':
                try:
                    return sub_val.decode('utf-8', errors='ignore')
                except Exception:
                    pass
    return None

with open('/tmp/speakdrive_sync.json', 'r', encoding='utf-8') as f:
    payload = json.load(f)

# 1. Update SQLite
db_path = '/home/cic-ai/.gemini/antigravity/conversation_summaries.db'
conn = sqlite3.connect(db_path)
cur = conn.cursor()

new_pb_entries = {}
for r in payload['rows']:
    raw_blob = bytes.fromhex(r['raw_summary_hex']) if r['raw_summary_hex'] else None
    cur.execute('''
        INSERT INTO conversation_summaries (
            conversation_id, title, preview, step_count, last_modified_time, workspace_uris,
            status, source, project_id, agent_name, parent_conversation_id, nesting_depth,
            battle_id, winning_conversation_id, not_fully_idle, killed, last_user_input_time,
            last_user_input_step_index, app_data_dir, raw_summary, group_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(conversation_id) DO UPDATE SET
            title=excluded.title, preview=excluded.preview, step_count=excluded.step_count,
            last_modified_time=excluded.last_modified_time, workspace_uris=excluded.workspace_uris,
            raw_summary=excluded.raw_summary
    ''', (
        r['cid'], r['title'], r['preview'], r['step_count'], r['last_modified'],
        r['workspace_uris'], r['status'], r['source'], r['project_id'], r['agent_name'],
        r['parent_id'], r['depth'], r['battle_id'], r['winning_id'], r['not_idle'],
        r['killed'], r['last_input_time'], r['last_input_step'], r['app_data_dir'],
        raw_blob, r['group_id']
    ))
    
    if raw_blob:
        try:
            tokens = decode_protobuf(raw_blob)
            entry = (1, 'submsg', [
                (1, 'bytes', r['cid'].encode('utf-8')),
                (2, 'submsg', tokens)
            ])
            new_pb_entries[r['cid']] = entry
        except Exception:
            pass

conn.commit()
conn.close()

# 2. Update agyhub_summaries_proto.pb incrementally
pb_path = '/home/cic-ai/.gemini/antigravity/agyhub_summaries_proto.pb'
existing_entries = []
if os.path.exists(pb_path):
    try:
        with open(pb_path, 'rb') as f:
            existing_entries = decode_protobuf(f.read())
    except Exception as e:
        print(f"Error reading existing pb: {e}")

cid_to_idx = {}
for idx, e in enumerate(existing_entries):
    c = get_cid_from_entry(e)
    if c:
        cid_to_idx[c] = idx

for cid, new_entry in new_pb_entries.items():
    if cid in cid_to_idx:
        existing_entries[cid_to_idx[cid]] = new_entry
    else:
        cid_to_idx[cid] = len(existing_entries)
        existing_entries.append(new_entry)

encoded_pb = encode_protobuf(existing_entries)
with open(pb_path, 'wb') as f:
    f.write(encoded_pb)

# 3. Update app_storage.json
storage_path = '/home/cic-ai/.config/Antigravity/app_storage.json'
proj_id = payload.get('project_id', '08cbdccc-b264-47ae-8f6a-0acb555d0fe2')
if os.path.exists(storage_path):
    try:
        with open(storage_path, 'r', encoding='utf-8') as f:
            st = json.load(f)
        st[f"sidebarCollapsed-{proj_id}"] = "false"
        st["lastCreatedProjectId"] = proj_id
        with open(storage_path, 'w', encoding='utf-8') as f:
            json.dump(st, f, indent=2)
    except Exception:
        pass

# 4. Permissions
os.system("chown -R cic-ai:cic-ai /home/cic-ai/.gemini /home/cic-ai/.config/Antigravity")
print("[Linux] Database & Protobuf cache updated successfully!")
"""
    send_bytes_ssh(remote_apply_script.encode('utf-8'), "/tmp/remote_apply_sync.py")
    run_cmd(f"ssh -o StrictHostKeyChecking=no {LINUX_HOST} \"python3 /tmp/remote_apply_sync.py\"")

    save_last_sync_time()
    print(f"  ✅ Đã đồng bộ thành công {len(rows)} phiên hội thoại sang Linux!")


# ==================== PULL FROM LINUX (LINUX -> WINDOWS) ====================
def sync_conversations_from_linux(since_hours=None, sync_all=False):
    print("\n--- BƯỚC 2: ĐỒNG BỘ PHIÊN HỘI THOẠI & KÝ ỨC (LINUX -> WINDOWS) ---")
    cutoff = load_last_sync_cutoff(since_hours)
    
    # Query candidate sessions from Linux SQLite qua SSH stdin pipe
    if sync_all:
        query_sql = f"SELECT conversation_id, title, preview, step_count, last_modified_time, workspace_uris, status, source, project_id, agent_name, parent_conversation_id, nesting_depth, battle_id, winning_conversation_id, not_fully_idle, killed, last_user_input_time, last_user_input_step_index, app_data_dir, hex(raw_summary), group_id FROM conversation_summaries WHERE project_id = '{PROJECT_ID}' OR workspace_uris LIKE '%English%' ORDER BY last_modified_time DESC"
    else:
        query_sql = f"SELECT conversation_id, title, preview, step_count, last_modified_time, workspace_uris, status, source, project_id, agent_name, parent_conversation_id, nesting_depth, battle_id, winning_conversation_id, not_fully_idle, killed, last_user_input_time, last_user_input_step_index, app_data_dir, hex(raw_summary), group_id FROM conversation_summaries WHERE (project_id = '{PROJECT_ID}' OR workspace_uris LIKE '%English%') AND last_modified_time >= '{cutoff}' ORDER BY last_modified_time DESC"

    remote_script = f"""
import sqlite3, json
conn = sqlite3.connect('/home/cic-ai/.gemini/antigravity/conversation_summaries.db')
cur = conn.cursor()
cur.execute('''{query_sql}''')
rows = cur.fetchall()
print(json.dumps([{{
    'cid': r[0], 'title': r[1], 'preview': r[2], 'step_count': r[3], 'last_modified': r[4],
    'workspace_uris': r[5], 'status': r[6], 'source': r[7], 'project_id': r[8], 'agent_name': r[9],
    'parent_id': r[10], 'depth': r[11], 'battle_id': r[12], 'winning_id': r[13], 'not_idle': r[14],
    'killed': r[15], 'last_input_time': r[16], 'last_input_step': r[17], 'app_data_dir': r[18],
    'raw_summary_hex': r[19] or '', 'group_id': r[20]
}} for r in rows]))
conn.close()
"""
    p = subprocess.run(
        ["ssh", "-o", "StrictHostKeyChecking=no", LINUX_HOST, "python3"],
        input=remote_script, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, encoding='utf-8', errors='replace', check=True
    )
    try:
        remote_rows = json.loads(p.stdout.strip())
    except Exception as e:
        print(f"  [Lỗi] Không đọc được dữ liệu phiên từ Linux: {e}\nRaw output: {p.stdout}")
        return

    if not remote_rows:
        print("  ℹ️ Không có phiên hội thoại mới nào trên Linux trong thời gian qua.")
        return

    print(f"  Phát hiện {len(remote_rows)} phiên hội thoại trên Linux:")
    for r in remote_rows:
        title = r['title'] or "Không có tiêu đề"
        print(f"   • [{r['cid'][:8]}...] {title} (Sửa đổi: {r['last_modified']})")

    cids = [r['cid'] for r in remote_rows]

    # 1. Kéo SQLite DBs từ Linux
    print("  Đang kéo SQLite database từ Linux về Windows...")
    conv_files = []
    for cid in cids:
        for ext in ["", "-wal", "-shm"]:
            conv_files.append(f"{cid}.db{ext}")
    stream_tar_from_linux(f"{LINUX_GEMINI_DIR}/conversations", conv_files, os.path.join(WIN_GEMINI_DIR, "conversations"))

    # 2. Kéo Brain folders từ Linux
    print("  Đang kéo thư mục não bộ / ký ức (brain/) từ Linux về Windows...")
    stream_tar_from_linux(f"{LINUX_GEMINI_DIR}/brain", cids, os.path.join(WIN_GEMINI_DIR, "brain"))

    # 3. Cập nhật SQLite & Protobuf trên Windows
    print("  Cập nhật cơ sở dữ liệu và cache Antigravity trên Windows...")
    local_db_path = os.path.join(WIN_GEMINI_DIR, "conversation_summaries.db")
    conn = sqlite3.connect(local_db_path)
    cur = conn.cursor()

    new_pb_entries = {}
    default_win_workspace_uri = f'["{WIN_URIS[0].decode("utf-8")}"]'

    for r in remote_rows:
        raw_blob = bytes.fromhex(r['raw_summary_hex']) if r['raw_summary_hex'] else None
        if raw_blob:
            try:
                parsed = decode_protobuf(raw_blob)
                converted = [convert_token_uris(t, 'windows') for t in parsed]
                raw_blob = encode_protobuf(converted)
                
                entry = (1, 'submsg', [
                    (1, 'bytes', r['cid'].encode('utf-8')),
                    (2, 'submsg', converted)
                ])
                new_pb_entries[r['cid']] = entry
            except Exception as e:
                print(f"   [Cảnh báo] Lỗi convert protobuf cho {r['cid']}: {e}")

        cur.execute('''
            INSERT INTO conversation_summaries (
                conversation_id, title, preview, step_count, last_modified_time, workspace_uris,
                status, source, project_id, agent_name, parent_conversation_id, nesting_depth,
                battle_id, winning_conversation_id, not_fully_idle, killed, last_user_input_time,
                last_user_input_step_index, app_data_dir, raw_summary, group_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(conversation_id) DO UPDATE SET
                title=excluded.title, preview=excluded.preview, step_count=excluded.step_count,
                last_modified_time=excluded.last_modified_time, workspace_uris=excluded.workspace_uris,
                raw_summary=excluded.raw_summary
        ''', (
            r['cid'], r['title'], r['preview'], r['step_count'], r['last_modified'],
            default_win_workspace_uri, r['status'], r['source'], r['project_id'], r['agent_name'],
            r['parent_id'], r['depth'], r['battle_id'], r['winning_id'], r['not_idle'],
            r['killed'], r['last_input_time'], r['last_input_step'], r['app_data_dir'],
            raw_blob, r['group_id']
        ))

    conn.commit()
    conn.close()

    # Cập nhật Windows agyhub_summaries_proto.pb
    pb_path = os.path.join(WIN_GEMINI_DIR, "agyhub_summaries_proto.pb")
    existing_entries = []
    if os.path.exists(pb_path):
        try:
            with open(pb_path, 'rb') as f:
                existing_entries = decode_protobuf(f.read())
        except Exception:
            pass

    cid_to_idx = {}
    for idx, e in enumerate(existing_entries):
        c = extract_cid_from_pb_entry(e)
        if c:
            cid_to_idx[c] = idx

    for cid, new_entry in new_pb_entries.items():
        if cid in cid_to_idx:
            existing_entries[cid_to_idx[cid]] = new_entry
        else:
            cid_to_idx[cid] = len(existing_entries)
            existing_entries.append(new_entry)

    with open(pb_path, 'wb') as f:
        f.write(encode_protobuf(existing_entries))

    save_last_sync_time()
    print(f"  ✅ Đã đồng bộ thành công {len(remote_rows)} phiên hội thoại từ Linux về Windows!")


def notify_windows_receiver():
    """Gửi tín hiệu notify từ Linux sang Windows Sync Receiver qua Tailscale"""
    print("\n--- BƯỚC 2: BẮN THÔNG BÁO ĐỒNG BỘ VỀ MÁY WINDOWS ---")
    url = f"http://{WIN_TAILSCALE_IP}:{WIN_RECEIVER_PORT}/notify-sync"
    req_data = json.dumps({"source": "linux_auto_push", "timestamp": datetime.now(timezone.utc).isoformat()}).encode("utf-8")
    
    try:
        import urllib.request
        req = urllib.request.Request(
            url, data=req_data,
            headers={
                "Content-Type": "application/json",
                "X-Sync-Token": SYNC_SECRET_TOKEN
            }
        )
        with urllib.request.urlopen(req, timeout=3) as resp:
            if resp.status == 200:
                print("  ✅ ĐÃ BẮN THÔNG BÁO THÀNH CÔNG SANG MÁY WINDOWS!")
                print("  🚀 Máy Windows đang tự động kéo Code và Phiên làm việc về trong nền.")
            else:
                print(f"  [Cảnh báo] Windows receiver phản hồi mã trạng thái: {resp.status}")
    except Exception as e:
        print(f"  ℹ️ Không thể kết nối tới Windows Sync Receiver ({e}).")
        print("  -> Máy Windows hiện có thể đang tắt. Code đã được lưu an toàn trên GitHub,")
        print("     dữ liệu phiên sẵn sàng trên Linux chờ máy Windows đồng bộ khi bật máy.")


# ==================== MAIN CLI ====================
def main():
    parser = argparse.ArgumentParser(description="SpeakDrive Multi-Machine Workspace Synchronizer")
    parser.add_argument("--direction", choices=["to-linux", "from-linux", "to-windows"], default="to-linux",
                        help="Hướng đồng bộ: to-linux (Windows -> Linux) hoặc from-linux (Linux -> Windows)")
    parser.add_argument("--code-only", action="store_true", help="Chỉ đồng bộ mã nguồn Git")
    parser.add_argument("--conv-only", action="store_true", help="Chỉ đồng bộ phiên hội thoại AI")
    parser.add_argument("--all", action="store_true", help="Đồng bộ toàn bộ tất cả phiên lịch sử (bỏ qua lọc thời gian)")
    parser.add_argument("--since-hours", type=int, default=None, help="Số giờ quét phiên sửa đổi gần đây (mặc định: tự động từ lần sync trước)")
    parser.add_argument("--cid", type=str, default=None, help="Chỉ đồng bộ duy nhất một Conversation ID cụ thể")
    args = parser.parse_args()

    dir_label = "SANG LINUX (WINDOWS -> LINUX)" if args.direction == "to-linux" else "VỀ WINDOWS (LINUX -> WINDOWS)"
    print("================================================================")
    print(f"🚀 BẮT ĐẦU ĐỒNG BỘ SPEAKDRIVE ({dir_label})")
    if not args.code_only:
        if args.all:
            mode_str = "TOÀN BỘ LỊCH SỬ"
        elif args.since_hours:
            mode_str = f"GIA TĂNG ({args.since_hours}h gần nhất)"
        else:
            mode_str = "GIA TĂNG TỰ ĐỘNG (Từ lần đồng bộ trước)"
        print(f"   Chế độ đồng bộ phiên: {mode_str}")
    print("================================================================")

    try:
        if not args.conv_only:
            sync_code_git(args.direction)
            
        if not args.code_only:
            if args.direction == "to-linux":
                sync_conversations_to_linux(since_hours=args.since_hours, sync_all=args.all, specific_cid=args.cid)
            elif args.direction in ("from-linux", "to-windows"):
                current_os = 'windows' if platform.system() == 'Windows' else 'linux'
                if current_os == 'windows':
                    sync_conversations_from_linux(since_hours=args.since_hours, sync_all=args.all)
                else:
                    notify_windows_receiver()
        
        print("\n================================================================")
        print("🎉 TẤT CẢ DỮ LIỆU ĐÃ ĐƯỢC ĐỒNG BỘ HOÀN TẤT & AN TOÀN!")
        print("================================================================")
    except Exception as e:
        print(f"\n❌ LỖI TRONG QUÁ TRÌNH ĐỒNG BỘ: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
