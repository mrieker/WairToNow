#!/bin/bash -v
mono --debug ReadArptDgmPng.exe aptdiags_300_$1/$2.png -csvoutfile x.csv -csvoutid $2 -stages -verbose
