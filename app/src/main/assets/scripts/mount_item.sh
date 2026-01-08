#!/system/bin/sh
# Mount a file (ISO/disk image) to a LUN for USB mass storage

# Get input parameters
FILE_PATH=$1
DISPLAY_NAME=$2
MODE=$3
ACTUAL_FILE_PATH=$4

# Find available LUN
CONTROLLER=$(getprop sys.usb.controller)
BASE_PATH='/config/usb_gadget/g1/functions/mass_storage.usb0'
SELECTED_LUN=""

MAX_DEVICES_MINUS1=$(($5 - 1))

i=0
while [ $i -le $MAX_DEVICES_MINUS1 ]; do
    FILE_NODE="$BASE_PATH/lun.$i/file"
    if [ -f "$FILE_NODE" ]; then
        CONTENT=$(cat "$FILE_NODE" 2>/dev/null | tr -d '[:space:]')
        if [ -z "$CONTENT" ]; then
            SELECTED_LUN=$i
            break
        fi
    fi
    i=$((i + 1))
done

if [ -z "$SELECTED_LUN" ]; then
  echo "Error: No free slots"
  exit 1
fi

# Configure LUN based on mode (read-only for ISO, read-write for Disk)
TARGET="$BASE_PATH/lun.$SELECTED_LUN"

if [ "$MODE" = "Disk" ] || [ "$MODE" = "disk" ]; then
    RO_VALUE=0
else
    RO_VALUE=1
fi

# Set LUN configuration
echo $RO_VALUE > "$TARGET/ro"
echo 1 > "$TARGET/removable"
printf '%s' "$DISPLAY_NAME" | cut -c1-16 > "$TARGET/inquiry_string"
echo "$ACTUAL_FILE_PATH" > "$TARGET/file"

echo "Success:$SELECTED_LUN"