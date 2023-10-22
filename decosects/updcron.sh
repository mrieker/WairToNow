#!/bin/bash -v
#
#  Run via cron every late Monday or early Tuesday
#  Does the update on the run that is 1 week before end-of-cycle
#
#  0 19 * * MON decosects/updcron.sh email@example.com > updcron.log 2>&1
#
#   $1 = email log here
#
cd `dirname $0`
set -e -x
export next28=0
oldcyc=`./cureffdate -28 -x -do  4 yyyy-mm-dd`
newcyc=`./cureffdate -28 -x -do 10 yyyy-mm-dd`
if [ "$oldcyc" != "$newcyc" ]
then
    export next28=1
    ./updateverything.sh > updateverything.log 2>&1
    if [ "$1" != "" ]
    then
        mail -s 'WairToNow Update' $1 < updateverything.log
    fi
fi
