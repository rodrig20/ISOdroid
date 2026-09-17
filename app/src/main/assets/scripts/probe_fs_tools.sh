#!/system/bin/sh
# Probe which disk image formats this kernel can create.
# Checks mkfs tool presence


# First match wins: absolute vendor path, then PATH lookup.
resolve_tool() {
    if [ -x "/vendor/bin/$1" ]; then
        echo "/vendor/bin/$1"
        return 0
    fi
    command -v "$1" 2>/dev/null
}

# True when a free loop device exists (no state change: lists only).
loop_available() {
    LOSETUP_BIN=$(command -v losetup 2>/dev/null)
    if [ -z "$LOSETUP_BIN" ]; then LOSETUP_BIN=/system/bin/losetup; fi
    if [ ! -x "$LOSETUP_BIN" ]; then return 1; fi
    FREE=$("$LOSETUP_BIN" -f 2>/dev/null)
    [ -n "$FREE" ]
}

OUT=""
# exfat: direct file write.
if [ -n "$(resolve_tool mkfs.exfat)" ]; then
    OUT="$OUT exfat=1"
else
    OUT="$OUT exfat=0:no-tool"
fi
# vfat32: newfs_msdos/mkfs.vfat plus a loop device (block device needed).
if [ -n "$(resolve_tool newfs_msdos)" ] || [ -n "$(resolve_tool mkfs.vfat)" ]; then
    if loop_available; then
        OUT="$OUT vfat32=1"
    else
        OUT="$OUT vfat32=0:no-loop"
    fi
else
    OUT="$OUT vfat32=0:no-tool"
fi
# ntfs: direct file write.
if [ -n "$(resolve_tool mkfs.ntfs)" ] || [ -n "$(resolve_tool mkntfs)" ]; then
    OUT="$OUT ntfs=1"
else
    OUT="$OUT ntfs=0:no-tool"
fi
# ext4: direct file write (-F).
if [ -n "$(resolve_tool mkfs.ext4)" ] || [ -n "$(resolve_tool mke2fs)" ]; then
    OUT="$OUT ext4=1"
else
    OUT="$OUT ext4=0:no-tool"
fi
# f2fs: block device needed.
if [ -n "$(resolve_tool mkfs.f2fs)" ]; then
    if loop_available; then
        OUT="$OUT f2fs=1"
    else
        OUT="$OUT f2fs=0:no-loop"
    fi
else
    OUT="$OUT f2fs=0:no-tool"
fi

echo "Success:$OUT"
