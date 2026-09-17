#!/system/bin/sh
# Create a disk image file with specified size, optionally formatted

# Get input parameters
FOLDER_PATH=$1
DISK_NAME=$2
SIZE_BYTES=$3
FSTYPE=$4

case "$FSTYPE" in
    vfat32|ntfs|ext4|f2fs|none) ;;
    *) FSTYPE=exfat ;;
esac

FULL_IMAGE_PATH="$FOLDER_PATH/${DISK_NAME}.img"
mkdir -p "$FOLDER_PATH" 2>/dev/null || true
if ! truncate -s "$SIZE_BYTES" "$FULL_IMAGE_PATH" 2>/dev/null; then
    echo "Error: Failed to create image with truncate"
    exit 1
fi

# Raw image, historical behavior: no filesystem.
if [ "$FSTYPE" = "none" ]; then
    echo "Success"
    exit 0
fi

# First match wins: absolute vendor path, then PATH lookup.
resolve_tool() {
    if [ -x "/vendor/bin/$1" ]; then
        echo "/vendor/bin/$1"
        return 0
    fi
    command -v "$1" 2>/dev/null
}

# Attach $1 (image file) to a free loop device: prints the node, or
# nothing when none is free. Caller detaches with detach_loop.
attach_loop() {
    LOSETUP_BIN=$(command -v losetup 2>/dev/null)
    if [ -z "$LOSETUP_BIN" ]; then LOSETUP_BIN=/system/bin/losetup; fi
    LOOPDEV=$("$LOSETUP_BIN" -f 2>/dev/null)
    if [ -z "$LOOPDEV" ]; then return 1; fi
    if ! "$LOSETUP_BIN" "$LOOPDEV" "$1" 2>/dev/null; then return 1; fi
    echo "$LOOPDEV"
    return 0
}

detach_loop() {
    LOSETUP_BIN=$(command -v losetup 2>/dev/null)
    if [ -z "$LOSETUP_BIN" ]; then LOSETUP_BIN=/system/bin/losetup; fi
    "$LOSETUP_BIN" -d "$1" 2>/dev/null || true
}

case "$FSTYPE" in
    exfat)
        TOOL=$(resolve_tool mkfs.exfat)
        if [ -z "$TOOL" ]; then
            echo "Error: exFAT formatting needs a missing tool (mkfs.exfat)"
            exit 1
        fi
        if "$TOOL" "$FULL_IMAGE_PATH" >/dev/null 2>&1; then
            echo "Success"
        else
            echo "Error: mkfs.exfat failed"
            exit 1
        fi
        ;;
    vfat32)
        TOOL=$(resolve_tool newfs_msdos)
        if [ -z "$TOOL" ]; then
            TOOL=$(resolve_tool mkfs.vfat)
        fi
        if [ -z "$TOOL" ]; then
            echo "Error: FAT32 formatting needs a missing tool (newfs_msdos/mkfs.vfat)"
            exit 1
        fi
        case "$TOOL" in
            *newfs_msdos)
                # newfs_msdos needs a block device: attach the image via loop.
                LOSETUP=$(command -v losetup 2>/dev/null)
                if [ -z "$LOSETUP" ]; then LOSETUP=/system/bin/losetup; fi
                LOOPDEV=$("$LOSETUP" -f 2>/dev/null)
                if [ -z "$LOOPDEV" ] || ! "$LOSETUP" "$LOOPDEV" "$FULL_IMAGE_PATH" 2>/dev/null; then
                    echo "Error: FAT32 formatting needs a free loop device"
                    exit 1
                fi
                # Sectors-per-cluster so small images still reach the 65525-cluster minimum.
                SECTORS=$((SIZE_BYTES / 512))
                SPC=1
                while [ $((SECTORS / SPC)) -gt 131072 ] && [ "$SPC" -lt 128 ]; do
                    SPC=$((SPC * 2))
                done
                "$TOOL" -F 32 -c "$SPC" "$LOOPDEV" >/dev/null 2>&1
                RC=$?
                "$LOSETUP" -d "$LOOPDEV" 2>/dev/null || true
                if [ "$RC" -eq 0 ]; then
                    echo "Success"
                elif [ "$SIZE_BYTES" -lt 35651584 ]; then
                    echo "Error: FAT32_TOO_SMALL"
                    exit 1
                else
                    echo "Error: FAT32 formatting failed"
                    exit 1
                fi
                ;;
            *)
                # dosfstools mkfs.vfat works directly on files.
                if "$TOOL" -F 32 "$FULL_IMAGE_PATH" >/dev/null 2>&1; then
                    echo "Success"
                elif [ "$SIZE_BYTES" -lt 35651584 ]; then
                    echo "Error: FAT32_TOO_SMALL"
                    exit 1
                else
                    echo "Error: FAT32 formatting failed"
                    exit 1
                fi
                ;;
        esac
        ;;
    ext4)
        TOOL=$(resolve_tool mkfs.ext4)
        if [ -z "$TOOL" ]; then
            TOOL=$(resolve_tool mke2fs)
        fi
        if [ -z "$TOOL" ]; then
            echo "Error: ext4 formatting needs a missing tool (mkfs.ext4)"
            exit 1
        fi
        case "$TOOL" in
            *mke2fs*) set -- "$TOOL" -t ext4 -F "$FULL_IMAGE_PATH" ;;
            *) set -- "$TOOL" -F "$FULL_IMAGE_PATH" ;;
        esac
        if "$@" >/dev/null 2>&1; then
            echo "Success"
        else
            echo "Error: mkfs.ext4 failed"
            exit 1
        fi
        ;;
    ntfs)
        TOOL=$(resolve_tool mkfs.ntfs)
        if [ -z "$TOOL" ]; then
            TOOL=$(resolve_tool mkntfs)
        fi
        if [ -z "$TOOL" ]; then
            echo "Error: NTFS formatting needs a missing tool (mkfs.ntfs)"
            exit 1
        fi
        # -f skips zeroing: fast even on GB images.
        if "$TOOL" -f "$FULL_IMAGE_PATH" >/dev/null 2>&1; then
            echo "Success"
        else
            echo "Error: mkfs.ntfs failed"
            exit 1
        fi
        ;;
    f2fs)
        TOOL=$(resolve_tool mkfs.f2fs)
        if [ -z "$TOOL" ]; then
            echo "Error: F2FS formatting needs a missing tool (mkfs.f2fs)"
            exit 1
        fi
        # Needs a block device: attach the image via loop.
        LOOPDEV=$(attach_loop "$FULL_IMAGE_PATH")
        if [ -z "$LOOPDEV" ]; then
            echo "Error: F2FS formatting needs a free loop device"
            exit 1
        fi
        "$TOOL" "$LOOPDEV" >/dev/null 2>&1
        RC=$?
        detach_loop "$LOOPDEV"
        if [ "$RC" -eq 0 ]; then
            echo "Success"
        else
            echo "Error: mkfs.f2fs failed"
            exit 1
        fi
        ;;
esac
exit 0
