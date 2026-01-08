#!/system/bin/sh
# Disable USB gadget and reset to default USB mode

# Disable current gadget
CONTROLLER=$(getprop sys.usb.controller)
echo "" > /config/usb_gadget/g1/UDC

# Clear all LUN file paths
for LUN_FILE in /config/usb_gadget/g1/functions/mass_storage.usb0/lun.*/file; do
    if [ -f "$LUN_FILE" ]; then
        echo -n "" > "$LUN_FILE"
    fi
done

# Remove mass storage function link
if [ -L /config/usb_gadget/g1/configs/b.1/f100 ]; then
    rm /config/usb_gadget/g1/configs/b.1/f100
fi

# Remove all additional LUN directories (keep lun.0)
for LUN_DIR in /config/usb_gadget/g1/functions/mass_storage.usb0/lun.*; do
    case "$LUN_DIR" in
        *.0) ;;
        *) rmdir "$LUN_DIR" 2>/dev/null ;;
    esac
done

# Set default USB configuration
setprop sys.usb.config mtp,adb
echo "$CONTROLLER" > /config/usb_gadget/g1/UDC
echo "Success"