#!/system/bin/sh
# Report whether our mass_storage gadget is currently bound.


# Initialize the gadget paths before checking the live state.
gadget_init || { echo "false"; exit 0; }

# The gadget is considered active only when the UDC is bound and the mass_storage link exists.
UDC_STATE=$(cat "$UDC_FILE" 2>/dev/null | tr -d '[:space:]')

HAS_LINK=0
for l in "$CONFIG_PATH"/*; do
    if [ -L "$l" ]; then
        T=$(readlink "$l" 2>/dev/null)
        case "$T" in
            *mass_storage*) HAS_LINK=1; break ;;
        esac
    fi
done
if [ -L "$CONFIG_PATH/f100" ]; then HAS_LINK=1; fi

# Return true only when both indicators confirm the gadget is live.
if [ "$HAS_LINK" -eq 1 ] && [ -n "$UDC_STATE" ]; then
    echo "true"
else
    echo "false"
fi
