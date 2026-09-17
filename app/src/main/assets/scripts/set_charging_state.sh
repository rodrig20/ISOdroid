#!/system/bin/sh
# Set charging state: $1 = "1" to suspend, anything else to allow.
# Last line is "Success" or "Error: ...".

case "$1" in
    1) S=1 ;;
    *) S=0 ;;
esac
INV=$((1 - S))

# True when the cable is physically connected.
cable_in() {
    P=$(cat /sys/class/power_supply/usb/present 2>/dev/null | tr -d '[:space:]')
    if [ "$P" = "1" ]; then return 0; fi
    P=$(cat /sys/class/power_supply/charger/online 2>/dev/null | tr -d '[:space:]')
    [ "$P" = "1" ]
}

# True when the battery is actually charging.
batt_charging() {
    CUR=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null | tr -d '[:space:]')
    case "$CUR" in
        ''|*[!0-9-]*) return 1 ;;
    esac
    [ "$CUR" -lt -100000 ]
}

# Wait for an effect: $1 = "suspended"|"charging", $2 = tenths of 0.5s.
wait_effect() {
    WANT=$1
    N=$2
    while [ "$N" -gt 0 ]; do
        if [ "$WANT" = "suspended" ]; then
            batt_charging || return 0
        else
            batt_charging && return 0
        fi
        sleep 0.5 2>/dev/null || sleep 1
        N=$((N - 1))
    done
    return 1
}

# Write $2 to $1 and verify by effect when cable is in.
# $1 = node, $2 = value, $3/$4 = suspend/resume timeouts.
try_node() {
    NODE=$1
    WANT=$2
    [ -f "$NODE" ] || return 1
    echo "$WANT" > "$NODE" 2>/dev/null || return 1
    if ! cable_in; then
        return 0
    fi
    if [ "$S" = "1" ]; then
        wait_effect suspended "$3"
    else
        wait_effect charging "$4"
    fi
}

if try_node /sys/class/power_supply/usb/input_suspend "$S" 12 20; then
    echo "Success"
    exit 0
fi
if try_node /sys/class/power_supply/battery/input_suspend "$S" 12 20; then
    echo "Success"
    exit 0
fi
for NODE in /sys/class/power_supply/battery/charging_enabled \
            /sys/class/power_supply/battery/battery_charging_enabled; do
    if try_node "$NODE" "$INV" 12 20; then
        echo "Success"
        exit 0
    fi
done

echo "Error: Charging control did not take effect (no usable input_suspend/charging_enabled node)"
exit 1
