#!/bin/bash
#
#  Create a .csv file for every chart.
#  It summarizes the .htm and .tfw data.
#  Takes a few seconds to run.
#

function readtiffnames
{
    while read tiffname
    do
        spacename="${tiffname%%.tif}"
        undername="${spacename// /_}"
        if [ ! -f "$spacename.htm" ]
        then
            continue
        fi
        if [ ! -f "$spacename.tfw" ]
        then
            continue
        fi
        if [ -f "$undername.csv" ]
        then
            continue
        fi
        echo @@@@@@@@@@@@@@@@@@@@@ $spacename
        mono --debug ../GenChartsCSV.exe \
                "$spacename"             \
                chartlist_all.htm
    done
}

cd `dirname $0`

cd charts

ls *.tif | readtiffnames

