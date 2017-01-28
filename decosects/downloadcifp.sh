#!/bin/bash -v
#
#  Download and decode CIFP data
#
#  Takes about 2 mins to run
#

set -e
cd `dirname $0`

airac28=`./cureffdate -28 airac`
cycles28=`./cureffdate -x -28 yyyymmdd`
cycles56=`./cureffdate -x yyyymmdd`

if [ ParseCifp.jar -ot ParseCifp.java ]
then
    rm -rf datums/iapcifps_$cycles28
    rm -f ParseCifp.jar *.class
    javac ParseCifp.java
    jar cf ParseCifp.jar *.class
    rm -f *.class
fi

if [ ! -f datums/iapcifps_$cycles28.gz ]
then
    wget http://www.aeronav.faa.gov/Upload_313-d/cifp/cifp_20$airac28.zip -O datums/iapcifps_$cycles28.zip
    unzip -p datums/iapcifps_$cycles28.zip CIFP_20$airac28/FAACIFP18 | gzip -c > datums/iapcifps_$cycles28.gz.tmp
    mv datums/iapcifps_$cycles28.gz.tmp datums/iapcifps_$cycles28.gz
    rm -f datums/iapcifps_$cycles28.zip
fi

if [ ! -d datums/iapcifps_$cycles28 ]
then
    export CLASSPATH=ParseCifp.jar
    rm -rf datums/iapcifps_$cycles28.tmp
    mkdir -p datums/iapcifps_$cycles28.tmp
    zcat datums/iapcifps_$cycles28.gz | java ParseCifp $cycles56 datums/iapcifps_$cycles28.tmp
    mv datums/iapcifps_$cycles28.tmp datums/iapcifps_$cycles28
fi
