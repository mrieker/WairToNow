#!/bin/bash

function readdirs
{
    lastname=
    while read name
    do
        lastname=$name
    done
    ls -l $lastname
    mono --debug ReadArptDgmPng.exe -markedpng ${faaid}_marked.png $lastname
    ls -l ${faaid}_marked.png
}

faaid=$1
ls datums/aptdiags_300_*/$faaid.png | readdirs
