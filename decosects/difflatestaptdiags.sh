#!/bin/bash -v
#
#  Find out which airport diagram georefs have changed since last revision if any
#
#  For each difference found, bring plate up in app and see if
#  WairToNow-drawn purple lines overlay FAA-drawn black runway lines
#

function findrevisions
{
    thisrev=
    lastrev=

    while read dirname
    do
        lastrev=$thisrev
        thisrev=$dirname
    done

    ls $thisrev $lastrev | sort -u | processcsvfile $thisrev $lastrev
}

function processcsvfile
{
    while read csvname
    do
        if [ "${csvname:2:4}" == ".csv" ]
        then
            echo Comparing $1/$csvname $2/$csvname
            mono --debug DiffArptDgmCsvs.exe $1/$csvname $2/$csvname
        fi
    done
}

cd `dirname $0`

if [ DiffArptDgmCsvs.exe -ot DiffArptDgmCsvs.cs ]
then
    gmcs -out:DiffArptDgmCsvs.exe DiffArptDgmCsvs.cs
fi

ls -d datums/apdgeorefs_* | findrevisions

