#!/bin/bash
#
#  Split all sectional chart .tif files
#  up into corresponding tiled .png files
#
#  Takes about 12mins to run
#
cd `dirname $0`
set -e

if [ "$1" != "" ]
then
    cd charts
    spacename=`basename "$1" .tif`
    undername="${spacename// /_}"
    if [ ! -f "$undername.wtn.zip" ]
    then
        if [ -f "$spacename.htm" ]
        then
            if [ -f "$spacename.tfw" ]
            then
                if [ ! -d "$undername" ]
                then
                    echo @@@@@@@@@@@@@@@@@@@@@ $spacename
                    mono --debug ../ReadTiffFile.exe "$spacename" > /dev/null
                fi
            fi
        fi
    fi
    exit
fi

rm -f tiffs.tmp
ls charts | grep '[.]tif$' > tiffs.tmp
if [ -s tiffs.tmp ]
then
    xargs -d '\n' -L 1 -P 12 ./readalltiffs.sh < tiffs.tmp
fi
rm -f tiffs.tmp
