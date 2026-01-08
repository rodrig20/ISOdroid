#!/system/bin/sh

# Script to validate if a path exists and is of the correct type
# Usage: validate_path.sh <path> <expected_type>
# Expected type: "file" or "dir"

if [ $# -ne 2 ]; then
    echo "Usage: validate_path.sh <path> <expected_type>"
    exit 1
fi

TEST_PATH="$1"
EXPECTED_TYPE="$2"

if [ -d "$TEST_PATH" ]; then
    if [ "$EXPECTED_TYPE" = "dir" ]; then
        echo "VALID_DIR"
    else
        echo "INVALID_TYPE"
    fi
elif [ -f "$TEST_PATH" ]; then
    if [ "$EXPECTED_TYPE" = "file" ]; then
        echo "VALID_FILE"
    else
        echo "INVALID_TYPE"
    fi
else
    echo "NOT_EXISTS"
fi
