#!/bin/bash
#
#  $1.. = state csv files to compare, eg, datums/iapgeorefs_20151015/*.csv
#
cd `dirname $0`
if [ CmpIAPToAvare.jar -ot CmpIAPToAvare.java ]
then
    rm -f CmpIAPToAvare.jar CmpIAPToAvare*.class Lib*.class
    javac -Xlint:deprecation CmpIAPToAvare.java Lib.java
    jar cf CmpIAPToAvare.jar CmpIAPToAvare*.class Lib*.class
    rm -f CmpIAPToAvare*.class Lib*.class
fi
if [ "$1" == "" ]
then
    cycles28=`cat datums/aptplates_expdate.dat`
    $0 datums/iapgeorefs_$cycles28/*.csv
else
    export CLASSPATH=CmpIAPToAvare.jar
    cat "$@" | java CmpIAPToAvare
fi
