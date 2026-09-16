#!/system/bin/sh
# Restore Android USB mode after disabling mass_storage.

gadget_init || exit 1

unbind_gadget
sleep 1

# Eject all media first so the host flushes cleanly.
for f in "$FUNC_PATH"/lun*/file; do
    if [ -f "$f" ]; then
        echo "" > "$f" 2>/dev/null || true
    fi
done

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

# Hand the controller back to Android's USB HAL.
setprop sys.usb.config mtp,adb 2>/dev/null || true
echo "Success"
