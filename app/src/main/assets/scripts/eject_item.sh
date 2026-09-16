#!/system/bin/sh
# Eject a LUN WITHOUT touching UDC/configs.

LUN_ID=$1
# Clear the file path to eject the item
CLEAN_ID=$(printf '%s' "$LUN_ID" | tr -cd '0-9')
if [ -z "$CLEAN_ID" ]; then
    echo "Error: Invalid LUN id"
    exit 1
fi

gadget_locate || exit 1

# Ejecting on an unbound gadget is a no-op for the host.
if ! gadget_is_bound; then
    echo "Error: Gadget is not bound, toggle USB gadget off and on again"
    exit 1
fi

TARGET=$(lun_dir_for "$CLEAN_ID")
LUN_FILE="$TARGET/file"

if [ ! -f "$LUN_FILE" ]; then
    echo "Error: LUN not found"
    exit 1
fi

if echo "" > "$LUN_FILE" 2>/dev/null; then
    echo "Success:$CLEAN_ID"
else
    echo "Error: Host locked this LUN (eject/unmount on the PC first, then retry)"
    exit 1
fi
