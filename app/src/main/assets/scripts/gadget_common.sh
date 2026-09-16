#!/system/bin/sh
# Shared helpers for ISOdroid USB gadget scripts.
# This file is prepended by RootManager before every script, so do NOT
# execute it directly.

# Log a gadget-related message to stderr.
gadget_log() {
    echo "[isodroid] $1" >&2
}

# Load the kernel modules required for USB gadget and mass storage support.
ensure_modules() {
    for mod in loop libcomposite usb_f_mass_storage; do
        modprobe "$mod" >/dev/null 2>&1 || true
    done
}

# Mount ConfigFS if the USB gadget tree is not already available.
ensure_configfs() {
    if [ ! -d /sys/kernel/config/usb_gadget ] && [ ! -d /config/usb_gadget ]; then
        mount -t configfs configfs /sys/kernel/config >/dev/null 2>&1 || true
    fi
}

# Resolve $CONFIG_ROOT to the configfs mount that holds usb_gadget.
detect_config_root() {
    if [ -d /sys/kernel/config/usb_gadget ]; then
        CONFIG_ROOT=/sys/kernel/config
        return 0
    fi
    if [ -d /config/usb_gadget ]; then
        CONFIG_ROOT=/config
        return 0
    fi
    # Try mounting, then re-check.
    mount -t configfs configfs /sys/kernel/config >/dev/null 2>&1 || true
    if [ -d /sys/kernel/config/usb_gadget ]; then
        CONFIG_ROOT=/sys/kernel/config
        return 0
    fi
    if [ -d /config/usb_gadget ]; then
        CONFIG_ROOT=/config
        return 0
    fi
    # Last resort: create the tree; kernel creates it on mkdir if supported.
    mkdir -p /config/usb_gadget >/dev/null 2>&1 || true
    if [ -d /config/usb_gadget ]; then
        CONFIG_ROOT=/config
        return 0
    fi
    return 1
}

# Detect the active USB controller for binding the gadget.
detect_udc_name() {
    # Prefer the live UDC list over the (often stale) system property.
    UDC_NAME=$(ls -1 /sys/class/udc 2>/dev/null | head -n1 | tr -d '[:space:]')
    if [ -z "$UDC_NAME" ]; then
        UDC_NAME=$(getprop sys.usb.controller 2>/dev/null | tr -d '[:space:]')
    fi
}

# Initialize the USB gadget paths and metadata needed for all later operations.
gadget_init() {
    ensure_modules
    ensure_configfs
    if ! detect_config_root; then
        echo "Error: ConfigFS usb_gadget not found"
        return 1
    fi

    # Reuse existing gadget if the ROM already created one, else g1.
    EXISTING=$(ls -1 "$CONFIG_ROOT/usb_gadget" 2>/dev/null | head -n1 | tr -d '[:space:]')
    if [ -n "$EXISTING" ]; then
        G_DIR="$CONFIG_ROOT/usb_gadget/$EXISTING"
    else
        G_DIR="$CONFIG_ROOT/usb_gadget/g1"
        mkdir -p "$G_DIR" >/dev/null 2>&1 || true
    fi

    # Resolve mass_storage function (do NOT create here; turn_on owns that).
    MASS_STORAGE=$(ls -1 "$G_DIR/functions" 2>/dev/null | grep '^mass_storage' | head -n1 | tr -d '[:space:]')
    if [ -z "$MASS_STORAGE" ]; then
        MASS_STORAGE="mass_storage.0"
    fi
    FUNC_PATH="$G_DIR/functions/$MASS_STORAGE"

    # Resolve config (b.1 on most devices, first child otherwise).
    CONFIG_NAME=$(ls -1 "$G_DIR/configs" 2>/dev/null | head -n1 | tr -d '[:space:]')
    if [ -z "$CONFIG_NAME" ]; then
        CONFIG_NAME="b.1"
    fi
    CONFIG_PATH="$G_DIR/configs/$CONFIG_NAME"

    UDC_FILE="$G_DIR/UDC"
    detect_udc_name
    return 0
}

# Clear the active UDC binding to detach the gadget from the host controller.
unbind_gadget() {
    if [ -f "$UDC_FILE" ]; then
        echo "" > "$UDC_FILE" 2>/dev/null || true
    fi
}

# Retry the UDC bind to handle temporary Android USB HAL re-bind races.
bind_gadget() {
    if [ -z "$UDC_NAME" ]; then
        echo "Error: No UDC controller found"
        return 1
    fi
    i=0
    while [ $i -lt 5 ]; do
        if echo "$UDC_NAME" > "$UDC_FILE" 2>/dev/null; then
            return 0
        fi
        sleep 1
        i=$((i + 1))
    done
    echo "Error: Cannot bind UDC $UDC_NAME (device busy, retry later)"
    return 1
}

# Find the first free LUN slot that is empty or not yet assigned.
find_free_lun() {
    MAX=$1
    i=0
    while [ $i -le "$MAX" ]; do
        if [ "$i" -eq 0 ] && [ -f "$FUNC_PATH/lun/file" ]; then
            NODE="$FUNC_PATH/lun/file"
        else
            NODE="$FUNC_PATH/lun.$i/file"
        fi
        if [ -f "$NODE" ]; then
            CONTENT=$(cat "$NODE" 2>/dev/null | tr -d '[:space:]')
            if [ -z "$CONTENT" ]; then
                echo "$i"
                return 0
            fi
        fi
        i=$((i + 1))
    done
    echo ""
    return 1
}

# Resolve the backing directory for a given LUN index across kernel variants.
lun_dir_for() {
    IDX=$1
    if [ "$IDX" -eq 0 ] && [ -d "$FUNC_PATH/lun" ]; then
        echo "$FUNC_PATH/lun"
    else
        echo "$FUNC_PATH/lun.$IDX"
    fi
}
