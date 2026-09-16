#!/system/bin/sh
# Restore Android USB mode after disabling mass_storage.

gadget_locate || exit 1

# Eject all media first so the host flushes cleanly.
for f in "$FUNC_PATH"/lun*/file; do
    if [ -f "$f" ]; then
        echo "" > "$f" 2>/dev/null || true
    fi
done

OLD_STATE=$(udc_state)
unbind_gadget
poll_until_success 5 udc_state_changed_from "$OLD_STATE" || true

# Remove only our mass_storage link (keep adb/ffs links).
for l in "$CONFIG_PATH"/*; do
    if [ -L "$l" ]; then
        T=$(readlink "$l" 2>/dev/null)
        case "$T" in
            *mass_storage*) rm "$l" 2>/dev/null || true ;;
        esac
    fi
done
rm -f "$CONFIG_PATH/f100" 2>/dev/null || true

# Remove leftover g2 gadget from older versions.
if [ -d "$CONFIG_ROOT/usb_gadget/g2" ]; then
    echo "" > "$CONFIG_ROOT/usb_gadget/g2/UDC" 2>/dev/null || true
    rm -f "$CONFIG_ROOT/usb_gadget/g2/configs/"*/* 2>/dev/null || true
    rmdir "$CONFIG_ROOT/usb_gadget/g2/functions/mass_storage.0/lun."* 2>/dev/null || true
    rmdir "$CONFIG_ROOT/usb_gadget/g2/functions/mass_storage.0" 2>/dev/null || true
    rmdir "$CONFIG_ROOT/usb_gadget/g2/functions" "$CONFIG_ROOT/usb_gadget/g2/configs/b.1" "$CONFIG_ROOT/usb_gadget/g2/configs" "$CONFIG_ROOT/usb_gadget/g2/strings/0x409" "$CONFIG_ROOT/usb_gadget/g2/strings" "$CONFIG_ROOT/usb_gadget/g2" 2>/dev/null || true
fi

# Hand the controller back to Android's USB HAL.
setprop sys.usb.config mtp,adb 2>/dev/null || true
echo "Success"
