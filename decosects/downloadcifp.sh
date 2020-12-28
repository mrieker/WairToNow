#!/bin/bash
#
#  Download and decode CIFP data
#
#  Takes about 3 mins to run
#
#  https://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/cifp/
#
#  http://aeronav.faa.gov/Upload_313-d/cifp/cifp_181011.zip

set -e

cd `dirname $0`

airac28=`./cureffdate -28 airac`
cycles28=`./cureffdate -x -28 yyyymmdd`

# download data file from FAA

if [ ! -f datums/iapcifps_$cycles28.gz ]
then
    effyyyymmdd=`./cureffdate -28 yyyymmdd`
    effyymmdd=${effyyyymmdd:2}
    wget -nv --no-check-certificate https://www.aeronav.faa.gov/Upload_313-d/cifp/cifp_$effyymmdd.zip -O datums/iapcifps_$cycles28.zip
    unzip -p datums/iapcifps_$cycles28.zip 'FAACIFP18' | gzip -c > datums/iapcifps_$cycles28.gz.tmp
    if [ `stat -c %s datums/iapcifps_$cycles28.gz.tmp` -lt 100000 ]
    then
        echo error downloading cifp file
        exit 1
    fi
    mv datums/iapcifps_$cycles28.gz.tmp datums/iapcifps_$cycles28.gz
    rm -f datums/iapcifps_$cycles28.zip
fi

# produce new FAA-provided data

if [ ! -d datums/iapcifps_$cycles28 ]
then
    export CLASSPATH=ParseCifp.jar
    rm -rf datums/iapcifps_$cycles28.tmp
    mkdir -p datums/iapcifps_$cycles28.tmp
    zcat datums/iapcifps_$cycles28.gz | java ParseCifp $cycles28 datums/iapcifps_$cycles28.tmp
    mv datums/iapcifps_$cycles28.tmp datums/iapcifps_$cycles28
fi
