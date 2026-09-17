# ISOdroid

ISOdroid is an Android app that turns your device into a bootable USB drive. With root access, it can mount disk image files (like `.iso`) and present them to a computer as a USB storage device, just like a flash drive.

> [!IMPORTANT]
> **Status:** This application is currently in its **testing phase**. You might encounter bugs or unexpected behavior depending on your kernel configuration. If you find any issues, please feel free to [open an issue](https://github.com/rodrig20/ISOdroid/issues) with your device logs and kernel version.

## Features

- **Mount Disk Images**: Pick any disk image on your device and mount it as a virtual USB drive.
- **USB Gadget Control**: Turn the USB gadget functionality on or off directly from the app.
- **Modern Interface**: Built with Jetpack Compose and Material 3 for a clean, modern user experience.
- **Manage Images**: Easily add or remove disk images from your mount list.
- **Power Management**: Optionally disable device charging to save battery when connected to a laptop.
- **Multiple Devices (LUNs)**: Supports multiple simulated storage devices. The maximum number of LUNs is set by the `FSG_MAX_LUNS` kernel macro.

## Requirements

- **Device & Kernel**: Tested on the **Redmi Note 12 Pro 5G** with the kernel: [kernel_xiaomi_mt6877](https://github.com/rajdeep-3305/kernel_xiaomi_mt6877). Should work on any AOSP kernel with ConfigFS (`CONFIG_USB_CONFIGFS_MASS_STORAGE`) and a writable `/config/usb_gadget` or `/sys/kernel/config/usb_gadget` (no kernel patch required).
- **Root Access Methods**: Successfully tested with **Sukisu**, but should also work with **Magisk**, **KernelSU**, or other root solutions.

## How to Use

1.  **Root Access**: The app needs root to manage USB gadget functionality. Make sure your device is rooted.
2.  **Grant Permissions**: Allow root access when prompted on first launch.
3.  **Enable USB Gadget**: Toggle "Enable USB Gadget" from the main screen.
4.  **Add an Image**: Tap the add (+) button and select a disk image (`.iso`, `.img`, etc.).
5.  **Connect to a PC**: Plug your device into a computer, the system will detect a new USB drive with the mounted image.
6.  **Unmount**: To remove the virtual drive, eject it directly from the app.

## Note

The mounted disk/LUN may use a filesystem (like F2FS) that some operating systems cannot recognise. You might need to format the virtual drive from your PC before use to ensure compatibility.

More than 8 LUNs need a host rescan: the kernel may support up to 16, but Linux's `usb-storage` only scans LUNs 0-7 per device. Reveal the rest with (replace `host0` with the phone's host):
```bash
for l in 8 9 10 11 12 13 14 15; do
    echo "0 0 $l" | sudo tee /sys/class/scsi_host/host0/scan > /dev/null
done
```

## Warning

ISOdroid performs low-level operations that require root. Misuse can cause system instability. Use at your own risk. The developers are not responsible for any damage to your device.
