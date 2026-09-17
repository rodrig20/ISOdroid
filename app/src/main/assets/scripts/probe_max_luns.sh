#!/system/bin/sh
# Probe the kernel's max LUN count without disturbing the live gadget; it creates a throwaway unbound probe and reports "Success:<max>" or "Error: ...".

gadget_locate || exit 1

PROBE_NAME="mass_storage.probe"
PROBE_PATH="$G_DIR/functions/$PROBE_NAME"
HARD_CAP=32

# Drop leftovers from an interrupted probe (our namespace only).
if [ -d "$PROBE_PATH" ]; then
    for d in "$PROBE_PATH"/lun.*; do
        if [ -d "$d" ]; then
            rmdir "$d" 2>/dev/null || true
        fi
    done
    rmdir "$PROBE_PATH" 2>/dev/null || true
fi

if ! mkdir "$PROBE_PATH" 2>/dev/null; then
    echo "Error: Cannot probe (disable the USB gadget and retry)"
    exit 1
fi

# Fresh instances may already contain lun.0: count what exists first.
COUNT=0
for d in "$PROBE_PATH"/lun.*; do
    if [ -d "$d" ]; then
        COUNT=$((COUNT + 1))
    fi
done

# Extend until the kernel refuses (FSG_MAX_LUNS) or the sanity cap.
i=$COUNT
while [ $i -lt "$HARD_CAP" ]; do
    if mkdir "$PROBE_PATH/lun.$i" 2>/dev/null; then
        COUNT=$((COUNT + 1))
        i=$((i + 1))
    elif [ -d "$PROBE_PATH/lun.$i" ]; then
        COUNT=$((COUNT + 1))
        i=$((i + 1))
    else
        break
    fi
done

# Cleanup: remove our LUNs, then the instance itself.
i=0
while [ $i -lt "$HARD_CAP" ]; do
    rmdir "$PROBE_PATH/lun.$i" 2>/dev/null || true
    i=$((i + 1))
done
rmdir "$PROBE_PATH" 2>/dev/null || true

if [ "$COUNT" -lt 1 ]; then
    echo "Error: Could not probe LUN limit"
    exit 1
fi
echo "Success:$COUNT"
