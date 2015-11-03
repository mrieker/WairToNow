#!/bin/bash
#
#  Split all sectional chart .tif files
#  up into corresponding tiled .png files
#
#  Takes about 25mins to run
#

set -e

function readtiffnames
{
    while read spacename
    do
        undername="${spacename// /_}"
        if [ ! -f "$spacename.htm" ]
        then
            continue
        fi
        if [ ! -f "$spacename.tfw" ]
        then
            continue
        fi
        if [ ! -d "$undername" ]
        then
            echo @@@@@@@@@@@@@@@@@@@@@ $spacename
            mono --debug ../ReadTiffFile.exe "$spacename" > /dev/null
        fi
    done
    rm -f $1
}

function splittiffnames
{
    n=0
    while read tiffname
    do
        spacename="${tiffname%%.tif}"
        n=$((n+1))
        echo "$spacename" >> readalltiffs.tmp.$n
        if [ $n == $1 ]
        then
            n=0
        fi
    done
}

cd `dirname $0`

if [ ReadTiffFile.exe -ot ReadTiffFile.cs ]
then
    gmcs -debug -out:ReadTiffFile.exe -reference:System.Drawing.dll ReadTiffFile.cs ChartTiff.cs
fi

cd charts

rm -f readalltiffs.tmp.*

ls *.tif | splittiffnames 6

readtiffnames readalltiffs.tmp.1 < readalltiffs.tmp.1 &
readtiffnames readalltiffs.tmp.2 < readalltiffs.tmp.2 &
readtiffnames readalltiffs.tmp.3 < readalltiffs.tmp.3 &
readtiffnames readalltiffs.tmp.4 < readalltiffs.tmp.4 &
readtiffnames readalltiffs.tmp.5 < readalltiffs.tmp.5 &
readtiffnames readalltiffs.tmp.6 < readalltiffs.tmp.6 &

while [ "`echo readalltiffs.tmp.*`" != "readalltiffs.tmp.*" ]
do
    sleep 10
done

