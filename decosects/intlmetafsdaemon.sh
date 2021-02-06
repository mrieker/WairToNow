#!/bin/bash
#
#  Start as a @reboot cron
#
cd `dirname $0`

function readlogs
{
    while read line
    do
        echo `date +%H:%M:%S` $line >> ../intlmetafsdaemon.`date +%Y-%m-%d`.log
    done
}

exec mono --debug IntlMetafsDaemon.exe 0 2>&1 | readlogs
