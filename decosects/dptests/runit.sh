#!/bin/bash
set -e
cd `dirname $0`
if [ ../DecodePlate.jar -ot ../DecodePlate.java ]
then
    ./makeit.sh
fi
export CLASSPATH=../DecodePlate.jar:../pdfbox-1.8.10.jar:../commons-logging-1.2.jar
set -x
exec java DecodePlate -markedpng x.png -verbose "$@"
