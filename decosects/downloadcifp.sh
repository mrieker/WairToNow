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

function mergeoldcifps
{
    while read statecsv
    do
        java MergeOldCifps \
                datums/iapcifps_faa_$cycles28/$statecsv \
                datums/iapcifps_save_20170302/$statecsv \
                datums/aptplates_$cycles28/state/$statecsv \
                datums/airports_$cycles28.csv \
                > datums/iapcifps_$cycles28.tmp/$statecsv.tmp
        mv datums/iapcifps_$cycles28.tmp/$statecsv.tmp datums/iapcifps_$cycles28.tmp/$statecsv
    done
}

cd `dirname $0`

airac28=`./cureffdate -28 airac`
cycles28=`./cureffdate -x -28 yyyymmdd`

# compile programs

if [ ParseCifp.jar -ot ParseCifp.java ]
then
    rm -rf datums/iapcifps_faa_$cycles28
    rm -f ParseCifp.jar *.class
    javac ParseCifp.java
    jar cf ParseCifp.jar *.class
    rm -f *.class
fi

if [ MergeOldCifps.jar -ot MergeOldCifps.java ]
then
    rm -rf datums/iapcifps_$cycles28
    rm -f MergeOldCifps.jar *.class
    javac MergeOldCifps.java
    jar cf MergeOldCifps.jar *.class
    rm -f *.class
fi

# download data file from FAA

if [ ! -f datums/iapcifps_$cycles28.gz ]
then
    effyyyymmdd=`./cureffdate -28 yyyymmdd`
    effyymmdd=${effyyyymmdd:2}
    wget -nv http://www.aeronav.faa.gov/Upload_313-d/cifp/cifp_$effyymmdd.zip -O datums/iapcifps_$cycles28.zip
    unzip -p datums/iapcifps_$cycles28.zip CIFP_20$airac28/FAACIFP18 | gzip -c > datums/iapcifps_$cycles28.gz.tmp
    mv datums/iapcifps_$cycles28.gz.tmp datums/iapcifps_$cycles28.gz
    rm -f datums/iapcifps_$cycles28.zip
fi

# produce new FAA-provided data

if [ ! -d datums/iapcifps_faa_$cycles28 ]
then
    export CLASSPATH=ParseCifp.jar
    rm -rf datums/iapcifps_faa_$cycles28 datums/iapcifps_faa_$cycles28.tmp
    mkdir -p datums/iapcifps_faa_$cycles28.tmp
    zcat datums/iapcifps_$cycles28.gz | java ParseCifp $cycles28 datums/iapcifps_faa_$cycles28.tmp
    mv datums/iapcifps_faa_$cycles28.tmp datums/iapcifps_faa_$cycles28
fi

# merge old FAA-provided data into new FAA-provided data

if [ ! -d datums/iapcifps_$cycles28 ]
then
    export CLASSPATH=MergeOldCifps.jar
    rm -rf datums/iapcifps_$cycles28 datums/iapcifps_$cycles28.tmp
    mkdir -p datums/iapcifps_$cycles28.tmp
    ls datums/iapcifps_faa_$cycles28 | mergeoldcifps
    mv datums/iapcifps_$cycles28.tmp datums/iapcifps_$cycles28
fi
