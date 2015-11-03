#!/bin/bash

function readnames
{
    last1=
    last2=
    while read name
    do
        last2=$last1
        last1=$name
    done
    set -x
    mono --debug DiffArptDgmCsvs.exe $last1 $last2
}

cd `dirname $0`
ls datums/aptdiags_300_*.csv | readnames
