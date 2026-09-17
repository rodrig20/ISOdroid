#!/system/bin/sh
# Mount a file (ISO/disk image) to a free LUN without re-enumerating the USB gadget

FILE_PATH=$1
DISPLAY_NAME=$2
MODE=$3
ACTUAL_FILE_PATH=$4
MAX_INDEX=$5
ARG_RO=$6
ARG_CDROM=$7

case "$MAX_INDEX" in
    ''|*[!0-9]*) MAX_INDEX=0 ;;
esac

gadget_locate || exit 1

# Never configure LUNs on an unbound gadget (invisible to the host).
if ! gadget_is_bound; then
    echo "Error: Gadget is not bound, toggle USB gadget off and on again"
    exit 1
fi

if [ ! -d "$FUNC_PATH" ]; then
    echo "Error: Gadget is off, enable USB gadget first"
    exit 1
fi

# The real backing file (Disk mode: folder/name.img already resolved by app).
if [ -n "$ACTUAL_FILE_PATH" ]; then
    BACKING="$ACTUAL_FILE_PATH"
else
    BACKING="$FILE_PATH"
fi
if [ ! -f "$BACKING" ] && [ ! -b "$BACKING" ]; then
    echo "Error: Backing file not found: $BACKING"
    exit 1
fi

SELECTED_LUN=$(find_free_lun "$MAX_INDEX" 2>/dev/null)
if [ -z "$SELECTED_LUN" ]; then
    echo "Error: No free slots"
    exit 1
fi
TARGET=$(lun_dir_for "$SELECTED_LUN")
FILE_NODE="$TARGET/file"

# Ensure the LUN is empty before touching flags.
echo "" > "$FILE_NODE" 2>/dev/null || true

# LUN flags, written while the LUN is still empty.
# Explicit per-item values win; empty falls back to
# the mode default (Disk rw, anything else ro). CD-ROM forces read-only.
if [ "$ARG_CDROM" = "1" ]; then
    CDROM_VALUE=1
else
    CDROM_VALUE=0
fi
if [ -n "$ARG_RO" ]; then
    case "$ARG_RO" in
        0) RO_VALUE=0 ;;
        *) RO_VALUE=1 ;;
    esac
elif [ "$MODE" = "Disk" ] || [ "$MODE" = "disk" ]; then
    RO_VALUE=0
else
    RO_VALUE=1
fi
if [ "$CDROM_VALUE" = "1" ]; then
    RO_VALUE=1
fi

echo "$RO_VALUE" > "$TARGET/ro" 2>/dev/null || true
echo 1 > "$TARGET/removable" 2>/dev/null || true
echo "$CDROM_VALUE" > "$TARGET/cdrom" 2>/dev/null || true
# inquiry_string is cosmetic (never fail the mount if the kernel refuses it bound).
printf '%s' "$DISPLAY_NAME" 2>/dev/null | cut -c1-16 > "$TARGET/inquiry_string" 2>/dev/null || true

if echo "$BACKING" > "$FILE_NODE" 2>/dev/null; then
    echo "Success:$SELECTED_LUN"
else
    echo "Error: Host locked this LUN (eject/unmount on the PC first, then retry)"
    exit 1
fi
