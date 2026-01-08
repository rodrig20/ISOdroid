#!/system/bin/sh
# Configure and enable USB gadget for mass storage

# Get max devices parameter
MAX_DEVICES=$1

# Initialize USB gadget
CONTROLLER=$(getprop sys.usb.controller)
G_DIR="/config/usb_gadget/g1"
FUNCTIONS_PATH="$G_DIR/functions/mass_storage.usb0"

echo "" > "$G_DIR/UDC"
setprop sys.usb.config none

# Set up mass storage functions
rm "$G_DIR/configs/b.1/f100" 2>/dev/null
rm -rf "$FUNCTIONS_PATH" 2>/dev/null
mkdir -p "$FUNCTIONS_PATH"

# Create LUNs for each device
i=0
while [ $i -lt $MAX_DEVICES ]; do
    mkdir -p "$FUNCTIONS_PATH/lun.$i"
    echo 1 > "$FUNCTIONS_PATH/lun.$i/removable"
    i=$((i + 1))
done

# Enable the gadget
ln -s "$FUNCTIONS_PATH" "$G_DIR/configs/b.1/f100"
echo "$CONTROLLER" > "$G_DIR/UDC"
