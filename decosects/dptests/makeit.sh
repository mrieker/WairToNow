#!/bin/bash
cd `dirname $0`
cd ..
set -v -e
rm -f DecodePlate.jar
export CLASSPATH=pdfbox-1.8.10.jar
javac -Xlint:deprecation DecodePlate.java Lib.java # -Xlint:unchecked
jar cf DecodePlate.jar DecodePlate*.class Lib*.class
rm -f DecodePlate*.class Lib*.class
