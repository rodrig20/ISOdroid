#!/system/bin/sh
# Set charging suspension state

# Get suspend value
SUSPEND_VALUE=$1

# Write suspend value to system file
echo $SUSPEND_VALUE > /sys/class/power_supply/battery/input_suspend 2>&1 || echo "Error: Cannot write to input_suspend"