#!/system/bin/sh
# Report charging state: last line is "0" (allowed), "1" (suspended) or "unsupported".

# Read a node, cleaned. Fails when missing/unreadable.
probe_node() {
    VAL=$(cat "$1" 2>/dev/null) || return 1
    VAL=$(printf '%s' "$VAL" | tr -d '[:space:]')
    printf '%s' "$VAL"
    return 0
}

# True when the cable is physically connected.
cable_in() {
    P=$(cat /sys/class/power_supply/usb/present 2>/dev/null | tr -d '[:space:]')
    if [ "$P" = "1" ]; then return 0; fi
    P=$(cat /sys/class/power_supply/charger/online 2>/dev/null | tr -d '[:space:]')
    [ "$P" = "1" ]
}

# True when the battery is actually charging (~-1A here).
batt_charging() {
    CUR=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null | tr -d '[:space:]')
    case "$CUR" in
        ''|*[!0-9-]*) return 1 ;;
    esac
    [ "$CUR" -lt -100000 ]
}

# Suspended by effect, for nodes whose readback self-clears.
effect_suspended() {
    cable_in && ! batt_charging
}

SUSP=0
HAVE=0

# usb/input_suspend: "1" vote trusted, "0" disambiguated via effect.
if R=$(probe_node /sys/class/power_supply/usb/input_suspend); then
    HAVE=1
    if [ "$R" = "1" ]; then
        SUSP=1
    elif effect_suspended; then
        SUSP=1
    fi
fi

# battery/input_suspend: "1" only counts with effect (readback lies on MTK).
if R=$(probe_node /sys/class/power_supply/battery/input_suspend); then
    HAVE=1
    if [ "$R" = "1" ]; then
        if ! cable_in || effect_suspended; then
            SUSP=1
        fi
    fi
fi

# Inverted backends: "0" means suspended.
for NODE in /sys/class/power_supply/battery/charging_enabled \
            /sys/class/power_supply/battery/battery_charging_enabled; do
    if R=$(probe_node "$NODE"); then
        HAVE=1
        if [ "$R" = "0" ]; then SUSP=1; fi
    fi
done

if [ "$HAVE" -eq 0 ]; then
    echo "unsupported"
    exit 0
fi
echo "$SUSP"
