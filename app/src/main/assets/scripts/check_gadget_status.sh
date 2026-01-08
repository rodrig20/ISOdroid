#!/system/bin/sh
# Check USB gadget status

# Get USB controller info
UDC_NAME=$(getprop sys.usb.controller)
UDC_STATE=$(cat /config/usb_gadget/g1/UDC 2>/dev/null)

# Check if gadget is active and return status
if [ -L /config/usb_gadget/g1/configs/b.1/f100 ] && [ "$UDC_STATE" = "$UDC_NAME" ]; then
   echo "true"
else
   echo "false"
fi