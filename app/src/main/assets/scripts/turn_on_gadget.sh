#!/system/bin/sh
# Configure and enable USB gadget for mass storage (single blink).

MAX_DEVICES=$1
case "$MAX_DEVICES" in
    ''|*[!0-9]*) MAX_DEVICES=1 ;;
esac
if [ "$MAX_DEVICES" -lt 1 ]; then MAX_DEVICES=1; fi

gadget_locate || exit 1

# Quiesce Android USB HAL so it does not re-bind mid-setup (main EBUSY source).
setprop sys.usb.config none 2>/dev/null || true
OLD_STATE=$(udc_state)
if ! gadget_is_unbound; then
    unbind_gadget
    poll_until_success 5 udc_state_changed_from "$OLD_STATE" || true
fi

# Remove only our mass_storage link (keep adb/ffs links intact).
for l in "$CONFIG_PATH"/*; do
    if [ -L "$l" ]; then
        T=$(readlink "$l" 2>/dev/null)
        case "$T" in
            *mass_storage*) rm "$l" 2>/dev/null || true ;;
        esac
    fi
done
rm -f "$CONFIG_PATH/f100" 2>/dev/null || true

# Pre-create empty LUNs before bind to avoid EBUSY on stock kernels
if ! gadget_ensure_function; then
    echo "Error: Could not set up mass_storage (toggle off and retry)"
    exit 1
fi
i=0
while [ $i -lt "$MAX_DEVICES" ]; do
    LUN_DIR="$FUNC_PATH/lun.$i"
    mkdir -p "$LUN_DIR" 2>/dev/null || true
    echo 1 > "$LUN_DIR/removable" 2>/dev/null || true
    i=$((i + 1))
done

ln -s "$FUNC_PATH" "$CONFIG_PATH/$FUNC_NAME" 2>/dev/null || true

# Drop stale LUNs beyond the new limit; only empty ones are removed and the bare lun dir is never touched.
i=$MAX_DEVICES
while [ $i -le 31 ]; do
    STALE_DIR="$FUNC_PATH/lun.$i"
    if [ -d "$STALE_DIR" ]; then
        CONTENT=$(cat "$STALE_DIR/file" 2>/dev/null | tr -d '[:space:]')
        if [ -z "$CONTENT" ]; then
            rmdir "$STALE_DIR" 2>/dev/null || true
        fi
    fi
    i=$((i + 1))
done

if ! bind_gadget; then
    echo "Error: Could not bind USB controller (toggle off and retry)"
    exit 1
fi

# Wait for host to configure us
await_live_gadget 15
RC=$?
if [ "$RC" -eq 0 ]; then
    echo "Success"
elif [ "$RC" -eq 2 ]; then
    echo "Success:waiting-host"
else
    echo "Error: Bind did not stick (unplug cable, enable, replug)"
    exit 1
fi
exit 0
