import os
import subprocess
import time
from pathlib import Path

t0 = time.time()
temp_dir = Path(os.environ.get("TEMP", "/tmp")) / "speakdrive_sync"
temp_dir.mkdir(parents=True, exist_ok=True)

conv_dir = Path(os.path.expanduser(r"~\.gemini\antigravity\conversations"))
sample_files = [f.name for f in conv_dir.glob("*.db")][:5]
print("Sample files:", sample_files)

list_file = temp_dir / "test_list.txt"
with open(list_file, "w", encoding="utf-8") as f:
    f.write("\n".join(sample_files))

tar_file = temp_dir / "test_conv.tar.gz"
print("Creating local tar...")
subprocess.run(["tar", "-czf", str(tar_file), "-C", str(conv_dir), "-T", str(list_file)], check=True)
print(f"Tar created: {tar_file.stat().st_size} bytes in {time.time() - t0:.2f}s")

print("Sending over SSH...")
t1 = time.time()
with open(tar_file, "rb") as f:
    res = subprocess.run(
        ["ssh", "-o", "StrictHostKeyChecking=no", "root@100.107.110.66", "tar -xzf - -C /home/cic-ai/.gemini/antigravity/conversations/"],
        input=f.read(),
        check=True
    )
print(f"SSH tar extract done in {time.time() - t1:.2f}s!")
