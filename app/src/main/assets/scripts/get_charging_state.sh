#!/system/bin/sh
# Get current charging suspension state

# Read charging suspension status
cat /sys/class/power_supply/battery/input_suspend 2>/dev/null || echo "0"