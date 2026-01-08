#!/system/bin/sh
# Eject a mounted item by clearing its file path

# Get LUN ID
LUN_ID=$1

# Clear the file path to eject the item
LUN_FILE="/config/usb_gadget/g1/functions/mass_storage.usb0/lun.$LUN_ID/file"

if [ -f "$LUN_FILE" ]; then
  echo "" > "$LUN_FILE"
  echo "Success:$LUN_ID"
else
  echo "Error: LUN not found"
fi