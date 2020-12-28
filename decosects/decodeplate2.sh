#!/bin/bash
set -e
cd `dirname $0`

rm -f x.csv
export CLASSPATH=DecodePlate2.jar:pdfbox-1.8.10.jar:commons-logging-1.2.jar
java DecodePlate2 -csvout x.csv -cycles28 20170202 -verbose "$@"
cat x.csv
