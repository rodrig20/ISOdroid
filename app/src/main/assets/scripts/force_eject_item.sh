#!/system/bin/sh
# Force-eject ONE LUN, bypassing the host's PREVENT-ALLOW MEDIUM REMOVAL lock.

LUN_ID=$1
CLEAN_ID=$(printf '%s' "$LUN_ID" | tr -cd '0-9')
if [ -z "$CLEAN_ID" ]; then
    echo "Error: Invalid LUN id"
    exit 1
fi

gadget_locate || exit 1

if ! gadget_is_bound; then
    echo "Error: Gadget is not bound, toggle USB gadget off and on again"
    exit 1
fi

TARGET=$(lun_dir_for "$CLEAN_ID")

if [ -f "$TARGET/forced_eject" ]; then
    if echo "" > "$TARGET/forced_eject" 2>/dev/null; then
        echo "Success:$CLEAN_ID"
        exit 0
    fi
    echo "Error: Force eject failed"
    exit 1
fi

# Kernels without forced_eject: fall back to a normal eject attempt so the
# caller gets the precise "host locked" reason instead.
LUN_FILE="$TARGET/file"
if [ ! -f "$LUN_FILE" ]; then
    echo "Error: LUN not found"
    exit 1
fi
if echo "" > "$LUN_FILE" 2>/dev/null; then
    echo "Success:$CLEAN_ID"
else
    echo "Error: Host locked this LUN and this kernel has no forced_eject (eject/unmount on the PC first)"
    exit 1
fi
