#!/bin/bash

function procit
{
    shift
    shift
    shift
    shift
    shift
    shift
    shift
    shift
    shift
    shift
    shift
    shift
    shift
    shift
    shift
    echo $@
}

function readit
{
    while read csvline
    do
        ssvline=${csvline//,/ }
        procit $ssvline
    done
}

cat charts/*.csv | readit | sort

