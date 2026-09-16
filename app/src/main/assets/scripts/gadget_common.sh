#!/system/bin/sh
#!/system/bin/sh
# Shared helpers for ISOdroid USB gadget scripts.
# This file is prepended by RootManager before every script, so do NOT
# execute it directly.
#
# Hard-won rules:
# - SINGLE bind/unbind each, NEVER retry loops: hammering UDC writes races
#   the HAL and panics the kernel (use-after-free in android_work -> reboot).
# - Operate INSIDE Android's g1: creating a g2 gadget fails with ENOMEM
#   while g1 holds the controller, and the HAL anchors ADB there anyway.
# - Status is always the LAST stdout line: RootManager merges stderr after
#   stdout, so keep helper stderr silent (2>/dev/null) or parsing breaks.

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
gadget_locate() {
    ensure_modules
    ensure_configfs
    if ! detect_config_root; then
        echo "Error: ConfigFS usb_gadget not found"
        return 1
    fi

    # Reuse existing gadget if the ROM already created one, else g1.
    if [ -d "$CONFIG_ROOT/usb_gadget/g1" ]; then
        G_DIR="$CONFIG_ROOT/usb_gadget/g1"
    else
        FIRST=$(ls -1 "$CONFIG_ROOT/usb_gadget" 2>/dev/null | head -n1 | tr -d '[:space:]')
        if [ -z "$FIRST" ]; then
            echo "Error: No USB gadget found"
            return 1
        fi
        G_DIR="$CONFIG_ROOT/usb_gadget/$FIRST"
    fi

    # Resolve mass_storage function (do NOT create here; turn_on owns that).
    EXISTING_FUNC=$(ls -1 "$G_DIR/functions" 2>/dev/null | grep '^mass_storage' | head -n1 | tr -d '[:space:]')
    if [ -n "$EXISTING_FUNC" ]; then
        FUNC_NAME="$EXISTING_FUNC"
    else
        FUNC_NAME="mass_storage.0"
    fi
    FUNC_PATH="$G_DIR/functions/$FUNC_NAME"

    # Resolve config (b.1 on most devices, first child otherwise).
    EXISTING_CFG=$(ls -1 "$G_DIR/configs" 2>/dev/null | head -n1 | tr -d '[:space:]')
    if [ -n "$EXISTING_CFG" ]; then
        CONFIG_NAME="$EXISTING_CFG"
    else
        CONFIG_NAME="b.1"
    fi
    CONFIG_PATH="$G_DIR/configs/$CONFIG_NAME"

    UDC_FILE="$G_DIR/UDC"
    detect_udc_name
    return 0
}

# Create our mass_storage instance (call only while unbound).
gadget_ensure_function() {
    mkdir -p "$FUNC_PATH" >/dev/null 2>&1 || true
    if [ ! -d "$FUNC_PATH" ]; then
        echo "Error: Cannot set up mass_storage (unbind first)"
        return 1
    fi
    return 0
}

# Clear the active UDC binding to detach the gadget from the host controller.
unbind_gadget() {
    if [ -f "$UDC_FILE" ]; then
        echo "" > "$UDC_FILE" 2>/dev/null || true
    fi
}

# Check if the gadget is currently bound to the UDC.
gadget_is_bound() {
    if [ -z "$UDC_NAME" ]; then return 1; fi
    CUR=$(cat "$UDC_FILE" 2>/dev/null | tr -d '[:space:]')
    [ -n "$CUR" ] && [ "$CUR" = "$UDC_NAME" ]
}

# Check if the gadget is currently unbound.
gadget_is_unbound() {
    CUR=$(cat "$UDC_FILE" 2>/dev/null | tr -d '[:space:]')
    [ -z "$CUR" ]
}

# Read the raw UDC driver state.
udc_state() {
    if [ -z "$UDC_NAME" ]; then return 1; fi
    cat "/sys/class/udc/$UDC_NAME/state" 2>/dev/null | tr -d '[:space:]'
}

# Check if the UDC state matches the expected value.
udc_state_is() {
    CUR=$(udc_state) || return 1
    [ "$CUR" = "$1" ]
}

# Check if the UDC state changed from a previous value.
udc_state_changed_from() {
    CUR=$(udc_state) || return 1
    [ "$CUR" != "$1" ]
}

# Poll a check function until it succeeds or the timeout runs out.
poll_until_success() {
    TENTHS=$1; shift
    while [ "$TENTHS" -gt 0 ]; do
        if "$@" >/dev/null 2>&1; then return 0; fi
        sleep 0.1 2>/dev/null || sleep 1
        TENTHS=$((TENTHS - 1))
    done
    return 1
}

# Wait for a live bind (bound + our link present + host configured).
await_live_gadget() {
    TENTHS=$1
    BAD=0
    while [ "$TENTHS" -gt 0 ]; do
        if gadget_is_bound && our_link_present; then
            BAD=0
            if [ "$(udc_state)" = "configured" ]; then
                return 0
            fi
        else
            BAD=$((BAD + 1))
            if [ "$BAD" -ge 3 ]; then
                return 1
            fi
        fi
        sleep 0.1 2>/dev/null || sleep 1
        TENTHS=$((TENTHS - 1))
    done
    if gadget_is_bound && our_link_present; then
        return 2
    fi
    return 1
}

# Check if our mass_storage link is present in the config.
our_link_present() {
    [ -L "$CONFIG_PATH/$FUNC_NAME" ] || [ -L "$CONFIG_PATH/f100" ] || [ -L "$CONFIG_PATH/mass_storage" ]
}

# Bind the UDC once and verify it stuck.
bind_gadget() {
    if [ -z "$UDC_NAME" ]; then
        echo "Error: No UDC controller found"
        return 1
    fi
    echo "$UDC_NAME" > "$UDC_FILE" 2>/dev/null || true
    if gadget_is_bound; then
        return 0
    fi
    echo "Error: Cannot bind UDC $UDC_NAME (controller busy)"
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
