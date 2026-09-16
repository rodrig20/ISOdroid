#!/system/bin/sh
# Report whether our mass_storage gadget is currently bound.

# Initialize the gadget paths before checking the live state.
gadget_locate || { echo "false"; exit 0; }

# The gadget is considered active only when the UDC is bound and the mass_storage link exists.
if gadget_is_bound && our_link_present; then
    echo "true"
else
    echo "false"
fi
