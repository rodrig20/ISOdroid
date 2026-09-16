#!/system/bin/sh
# Configure and enable USB gadget for mass storage (single blink).

MAX_DEVICES=$1
case "$MAX_DEVICES" in
    ''|*[!0-9]*) MAX_DEVICES=1 ;;
esac
if [ "$MAX_DEVICES" -lt 1 ]; then MAX_DEVICES=1; fi

gadget_init || exit 1

# Quiesce Android USB HAL so it does not re-bind mid-setup (main EBUSY source).
setprop sys.usb.config none 2>/dev/null || true
unbind_gadget
sleep 1

mkdir -p "$FUNC_PATH" "$CONFIG_PATH" 2>/dev/null || true

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
i=0
while [ $i -lt "$MAX_DEVICES" ]; do
    LUN_DIR="$FUNC_PATH/lun.$i"
    mkdir -p "$LUN_DIR" 2>/dev/null || true
    echo 1 > "$LUN_DIR/removable" 2>/dev/null || true
    i=$((i + 1))
done

ln -s "$FUNC_PATH" "$CONFIG_PATH/f100" 2>/dev/null || true

if bind_gadget; then
    echo "Success"
else
    exit 1
fi
