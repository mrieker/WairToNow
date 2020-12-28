#!/bin/bash

if [ "$1" != "" ]
then
    if [ "$1" == "ENR" ]
    then
        two=$2
        two=${two%:}
        if [ ! -f ENR_${two}_20151210.png ]
        then
            set -x
            mono --debug VerifyOutline.exe "ENR $two 20151210"
        fi
    fi
else
    xargs -L 1 -P 9 $0 < ../webpages/outlines.txt
fi

