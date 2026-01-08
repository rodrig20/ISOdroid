#!/system/bin/sh
# Create a disk image file with specified size

# Get input parameters
FOLDER_PATH=$1
DISK_NAME=$2
SIZE_BYTES=$3

# Create disk image
set -e
FULL_IMAGE_PATH="$FOLDER_PATH/${DISK_NAME}.img"
mkdir -p "$FOLDER_PATH"
truncate -s $SIZE_BYTES "$FULL_IMAGE_PATH"

# Check if creation was successful
if [ $? -eq 0 ]; then
  echo "Success"
else
  echo "Error: Failed to create image with truncate"
fi