#!/bin/bash

set -eu
IFS=","
for PARTIAL_PATH in $1
do
    echo "PROCESSING: ${PARTIAL_PATH}"
    TEMP_DIR="$(mktemp -d)"
    echo "Using Temporary Directory: ${TEMP_DIR}"
    cd "${TEMP_DIR}"

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


    BANDCOUNT="$(rio info ${JPEG2000_FILE} | perl -wne '/"count": (\d+),/i and print $1')"
    echo "Number of bands: ${BANDCOUNT}"

    if [ "${BANDCOUNT}" -gt "3" ]; then
        echo "CREATING COG $COG_FILE with LZW profile"
        rio cogeo -p lzw --co BLOCKXSIZE=256 --co BLOCKYSIZE=256 "${TIF_FILE}" "${COG_FILE}"
    else
        echo "CREATING COG $COG_FILE with JPG profile"
        rio cogeo --co BLOCKXSIZE=256 --co BLOCKYSIZE=256 "${TIF_FILE}" "${COG_FILE}"
    fi

    echo "Uploading ${COG_FILE} => ${S3_UPLOAD_PATH}"
    aws s3 cp "${COG_FILE}" "${S3_UPLOAD_PATH}"

    cd /tmp/
    rm -rf "${TEMP_DIR}"
done
