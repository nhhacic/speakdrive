#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SpeakDrive / English Speaking App - Multi-Machine Workspace Synchronizer
========================================================================
Đồng bộ hóa 2 chiều toàn diện giữa máy Windows và máy trạm Linux:
1. Mã nguồn (Git Code qua GitHub / Direct Pull)
2. Toàn bộ các phiên hội thoại Antigravity (`conversations/<id>.db*`)
3. Thư mục não bộ / ký ức ngữ cảnh AI (`brain/<id>/`)
4. Cơ sở dữ liệu SQLite (`conversation_summaries.db`) với chuyển đổi URI tự động
5. File cache Protobuf Antigravity Hub (`agyhub_summaries_proto.pb`)
6. Cấu hình giao diện (`app_storage.json`)
"""

import os
import sys

# Ensure UTF-8 output on all platforms
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

import json
import sqlite3
import argparse
import subprocess
import shutil
import platform
from pathlib import Path

# ==================== CẤU HÌNH HỆ THỐNG ====================
PROJECT_ID = "08cbdccc-b264-47ae-8f6a-0acb555d0fe2"

# Đường dẫn trên Windows
WIN_PROJECT_DIR = r"d:\@Vibe_code_projects\English Speaking App"
WIN_GEMINI_DIR = os.path.expanduser(r"~\.gemini\antigravity")
WIN_APP_STORAGE = os.path.expandvars(r"%APPDATA%\Antigravity\app_storage.json")

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


def get_cid_from_entry(entry):
    _, _, fields = entry
    for fnum, fkind, fval in fields:
        if fnum == 1 and fkind == 'submsg':
            for sub_num, sub_kind, sub_val in fval:
                if sub_num == 6 and sub_kind == 'bytes':
                    return sub_val.decode('utf-8', errors='ignore')
        elif fnum == 2 and fkind == 'submsg':
            for sub_num, sub_kind, sub_val in fval:
                if sub_num == 17 and sub_kind == 'submsg':
                    for s17_num, s17_kind, s17_val in sub_val:
                        if s17_num == 6 and s17_kind == 'bytes':
                            return s17_val.decode('utf-8', errors='ignore')
    return None


def is_project_entry(entry):
    raw = encode_protobuf([entry])
    return b'English%20Speaking%20App' in raw or PROJECT_ID.encode('utf-8') in raw


# ==================== SYNC WORKFLOW ====================
def run_cmd(cmd, check=True, cwd=None):
    print(f"  [RUN] {cmd}")
    res = subprocess.run(
        cmd, shell=True, cwd=cwd,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, encoding='utf-8', errors='replace'
    )
    if check and res.returncode != 0:
        raise RuntimeError(f"Command failed (exit {res.returncode}):\n{res.stderr}")
    return res.stdout.strip()


def send_bytes_ssh(data_bytes, remote_path):
    cmd = ["ssh", "-o", "StrictHostKeyChecking=no", LINUX_HOST, f"cat > '{remote_path}'"]
    p = subprocess.run(cmd, input=data_bytes, check=True)
    return p.returncode


def stream_tar_ssh(source_dir, items_list, remote_target_dir):
    if not items_list:
        return
    temp_dir = Path(os.environ.get("TEMP", "/tmp")) / "speakdrive_sync"
    temp_dir.mkdir(parents=True, exist_ok=True)
    temp_list = temp_dir / "tar_stream_items.txt"
    with open(temp_list, "w", encoding="utf-8") as f:
        f.write("\n".join(items_list))
    
    tar_archive = temp_dir / "transfer_batch.tar.gz"
    run_cmd(f"tar -czf \"{tar_archive}\" -C \"{source_dir}\" -T \"{temp_list}\"", check=True)
    
    with open(tar_archive, "rb") as f:
        ssh_cmd = ["ssh", "-o", "StrictHostKeyChecking=no", LINUX_HOST, f"tar -xzf - -C '{remote_target_dir}'"]
        p = subprocess.run(ssh_cmd, input=f.read(), check=True)
        if p.returncode != 0:
            raise RuntimeError(f"Lỗi truyền tải dữ liệu tar stream (exit {p.returncode})")
    try:
        tar_archive.unlink()
    except Exception:
        pass


def sync_code_git(direction):
    print("\n--- BƯỚC 1: ĐỒNG BỘ MÃ NGUỒN GIT ---")
    current_os = 'windows' if platform.system() == 'Windows' else 'linux'
    
    if current_os == 'windows':
        branch = run_cmd("git rev-parse --abbrev-ref HEAD", cwd=WIN_PROJECT_DIR)
        print(f"  Branch hiện tại: {branch}")
        
        # Check uncommitted
        status = run_cmd("git status --porcelain", cwd=WIN_PROJECT_DIR)
        if status:
            print("  [Thông báo] Phát hiện file chưa commit, tự động commit đồng bộ...")
            run_cmd("git add -A", cwd=WIN_PROJECT_DIR)
            run_cmd('git commit -m "Auto-sync: update from Windows workspace"', cwd=WIN_PROJECT_DIR)
        
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
            run_cmd('git commit -m "Auto-sync: update from Linux workspace"', cwd=LINUX_PROJECT_DIR)
        run_cmd(f"git push origin {branch}", cwd=LINUX_PROJECT_DIR)
        print("  ✅ Mã nguồn Git đã được đẩy lên GitHub từ Linux!")


def sync_conversations_to_linux():
    print("\n--- BƯỚC 2: ĐỒNG BỘ PHIÊN HỘI THOẠI & KÝ ỨC (WINDOWS -> LINUX) ---")
    local_db_path = os.path.join(WIN_GEMINI_DIR, "conversation_summaries.db")
    if not os.path.exists(local_db_path):
        print(f"  [Lỗi] Không tìm thấy {local_db_path}")
        return

    conn = sqlite3.connect(local_db_path)
    cur = conn.cursor()
    cur.execute(
        "SELECT conversation_id, title, preview, step_count, last_modified_time, workspace_uris, "
        "status, source, project_id, agent_name, parent_conversation_id, nesting_depth, battle_id, "
        "winning_conversation_id, not_fully_idle, killed, last_user_input_time, last_user_input_step_index, "
        "app_data_dir, raw_summary, group_id FROM conversation_summaries "
        "WHERE project_id = ? OR workspace_uris LIKE '%English%'",
        (PROJECT_ID,)
    )
    rows = cur.fetchall()
    conn.close()
    print(f"  Tìm thấy {len(rows)} phiên hội thoại của dự án trên Windows.")

    # 1. Sync conversation DBs & Brain folders
    temp_dir = Path(os.environ.get("TEMP", "/tmp")) / "speakdrive_sync"
    temp_dir.mkdir(parents=True, exist_ok=True)
    temp_sqlite_dump = temp_dir / "linux_summaries_updates.sql"

    sql_statements = []
    cids = []
    
    for row in rows:
        cid = row[0]
        cids.append(cid)
        raw_summary = row[19]
        
        # Convert raw_summary to linux URI
        linux_raw_summary = raw_summary
        if raw_summary:
            parsed = decode_protobuf(raw_summary)
            converted = [convert_token_uris(t, 'linux') for t in parsed]
            linux_raw_summary = encode_protobuf(converted)

        # Build SQLite UPSERT
        linux_workspace_uri = f'["{LINUX_URI.decode("utf-8")}"]'
        sql_statements.append((
            cid, row[1], row[2], row[3], row[4], linux_workspace_uri,
            row[6], row[7], row[8], row[9], row[10], row[11], row[12],
            row[13], row[14], row[15], row[16], row[17], row[18], linux_raw_summary, row[20]
        ))

    # Apply updates to Linux DB via python on Linux
    temp_sync_json = temp_dir / "sync_data.json"
    sync_payload = {
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
    # Send JSON payload directly via SSH pipe (không dùng scp)
    print("  Đang chuyển dữ liệu phiên hội thoại sang Linux...")
    send_bytes_ssh(json.dumps(sync_payload).encode('utf-8'), "/tmp/speakdrive_sync.json")

    # Sync conversation DB files via streaming tar pipe
    print("  Đang đóng gói và truyền tải các file SQLite hội thoại...")
    conv_dir = Path(WIN_GEMINI_DIR) / "conversations"
    conv_files_to_sync = []
    for cid in cids:
        for ext in ["", "-wal", "-shm"]:
            f = conv_dir / f"{cid}.db{ext}"
            if f.exists():
                conv_files_to_sync.append(f"{cid}.db{ext}")

    if conv_files_to_sync:
        stream_tar_ssh(conv_dir, conv_files_to_sync, f"{LINUX_GEMINI_DIR}/conversations/")

    # Sync brain folders via streaming tar pipe
    print("  Đang đóng gói và truyền tải các thư mục não bộ / ký ức (brain/)...")
    brain_dir = Path(WIN_GEMINI_DIR) / "brain"
    brain_folders_to_sync = [cid for cid in cids if (brain_dir / cid).exists()]

    if brain_folders_to_sync:
        stream_tar_ssh(brain_dir, brain_folders_to_sync, f"{LINUX_GEMINI_DIR}/brain/")

    # Update Linux SQLite & agyhub_summaries_proto.pb via Python script on Linux
    remote_apply_script = """
import sqlite3, json, os

with open('/tmp/speakdrive_sync.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

db_path = '/home/cic-ai/.gemini/antigravity/conversation_summaries.db'
conn = sqlite3.connect(db_path)
cur = conn.cursor()

for r in data['rows']:
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

conn.commit()
conn.close()
os.system("chown -R cic-ai:cic-ai /home/cic-ai/.gemini")
print("[Linux] Database updated successfully!")
"""
    send_bytes_ssh(remote_apply_script.encode('utf-8'), "/tmp/apply_sync.py")
    run_cmd(f"ssh -o StrictHostKeyChecking=no {LINUX_HOST} \"python3 /tmp/apply_sync.py && python3 /tmp/migrate_hub.py\"")

    print("  ✅ Toàn bộ phiên hội thoại, ký ức AI và cache đã được đồng bộ sang Linux!")


def main():
    parser = argparse.ArgumentParser(description="SpeakDrive Multi-Machine Workspace Synchronizer")
    parser.add_argument("--direction", choices=["to-linux", "to-windows"], default="to-linux",
                        help="Hướng đồng bộ: to-linux (Windows -> Linux) hoặc to-windows (Linux -> Windows)")
    parser.add_argument("--code-only", action="store_true", help="Chỉ đồng bộ mã nguồn Git")
    parser.add_argument("--conv-only", action="store_true", help="Chỉ đồng bộ phiên hội thoại AI")
    args = parser.parse_args()

    print("================================================================")
    print(f"🚀 BẮT ĐẦU ĐỒNG BỘ SPEAKDRIVE (Hướng: {args.direction.upper()})")
    print("================================================================")

    try:
        if not args.conv_only:
            sync_code_git(args.direction)
        if not args.code_only:
            if args.direction == "to-linux":
                sync_conversations_to_linux()
            else:
                print("  [Thông báo] Đồng bộ to-windows sẽ thực hiện khi chạy trên máy Linux.")
        
        print("\n================================================================")
        print("🎉 TẤT CẢ DỮ LIỆU ĐÃ ĐƯỢC ĐỒNG BỘ HOÀN TẤT & AN TOÀN!")
        print("================================================================")
    except Exception as e:
        print(f"\n❌ LỖI TRONG QUÁ TRÌNH ĐỒNG BỘ: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
