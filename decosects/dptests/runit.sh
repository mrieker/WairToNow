#!/bin/bash
set -e
cd `dirname $0`
if [ ../DecodePlate.jar -ot ../DecodePlate.java ]
then
    ./makeit.sh
fi
export CLASSPATH=../DecodePlate.jar:../pdfbox-1.8.10.jar:../commons-logging-1.2.jar
export next28=1
cycles28=`../cureffdate -28 -x yyyymmdd`
cycles56=`../cureffdate     -x yyyymmdd`
set -x
exec java DecodePlate -cycles28 $cycles28 -cycles56 $cycles56 -markedpng x.png -verbose "$@"
