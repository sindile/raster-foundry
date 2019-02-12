#!/bin/bash

set -eu

TEMP_DIR="$(mktemp -d)"
echo "Using Temporary Directory: ${TEMP_DIR}"
cd "${TEMP_DIR}"

FILENAME="$1"
TIF_FILE="$(echo ${FILENAME} | rev | cut -c 4- | rev | sed 's/.*/&tif/')"
COG_FILE="cog-${TIF_FILE}"

REMOTE_FILE="ftp://ftp.pasda.psu.edu/pub/pasda/philacity/data/Imagery2018/${FILENAME}"
S3_UPLOAD_PATH="s3://rasterfoundry-production-data-us-east-1/${TIF_FILE}"
LOCAL_TEMP="temp.zip"

echo "Downloading $REMOTE_FILE => $LOCAL_TEMP"
curl "$REMOTE_FILE" > "$LOCAL_TEMP"
unzip temp.zip


echo "CREATING COG $COG_FILE"
rio cogeo --co BLOCKXSIZE=256 --co BLOCKYSIZE=256 "${TIF_FILE}" "${COG_FILE}"

echo "Uploading ${COG_FILE} => ${S3_UPLOAD_PATH}"
aws s3 cp "${COG_FILE}" "${S3_UPLOAD_PATH}"

cd /tmp/
rm -rf "${TEMP_DIR}"
