#!/bin/bash

set -eu

TEMP_DIR="$(mktemp -d)"
echo "Using Temporary Directory: ${TEMP_DIR}"
cd "${TEMP_DIR}"

PARTIAL_PATH="$1"
FILENAME="$(echo ${PARTIAL_PATH} | perl -wne '/\/(\w+\.zip)$/i and print $1')"
REMOTE_FILE="https://prd-tnm.s3.amazonaws.com/${PARTIAL_PATH}"
TIF_FILE="$(echo ${FILENAME} | rev | cut -c 4- | rev | sed 's/.*/&tif/')"
COG_FILE="cog-${TIF_FILE}"
JPEG2000_FILE="$(echo ${FILENAME} | rev | cut -c 4- | rev | sed 's/.*/&jp2/')"
STATE="$(echo ${PARTIAL_PATH} | perl -wne '/HRO\/(\w+)\/data/i and print $1')"

S3_UPLOAD_PATH="s3://rasterfoundry-production-data-us-east-1/naip-hro/${STATE}/${TIF_FILE}"
LOCAL_TEMP="temp.zip"

echo "Downloading $REMOTE_FILE => $LOCAL_TEMP"
curl "$REMOTE_FILE" > "$LOCAL_TEMP"
unzip temp.zip

echo "CREATING TIF: ${JPEG2000_FILE} ${TIF_FILE}"
gdal_translate ${JPEG2000_FILE} ${TIF_FILE}

echo "CREATING COG $COG_FILE"
rio cogeo --co BLOCKXSIZE=256 --co BLOCKYSIZE=256 "${TIF_FILE}" "${COG_FILE}"

echo "Uploading ${COG_FILE} => ${S3_UPLOAD_PATH}"
aws s3 cp "${COG_FILE}" "${S3_UPLOAD_PATH}"

cd /tmp/
rm -rf "${TEMP_DIR}"
