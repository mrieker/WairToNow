#!/bin/bash -v
set -e
cd `dirname $0`

if [ DecodePlate2.jar -ot DecodePlate2.java]
then
    export CLASSPATH=pdfbox-1.8.10.jar:commons-logging-1.2.jar
    rm -f DecodePlate2.jar DecodePlate2*.class Lib*.class
    javac -Xlint DecodePlate2.java Lib.java
    jar cf DecodePlate2.jar DecodePlate2*.class Lib*.class
    rm -f DecodePlate2*.class Lib*.class
fi

rm -f x.csv
export CLASSPATH=DecodePlate2.jar:pdfbox-1.8.10.jar:commons-logging-1.2.jar
java DecodePlate2 -csvout x.csv -cycles28 20170202 -verbose "$@"
cat x.csv
