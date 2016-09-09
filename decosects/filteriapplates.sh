#!/bin/bash
#
#  Filter plate names to get IAPs
#
grep ',"IAP-' $1 | grep -v ',"IAP-TACAN' | grep -v ',"IAP-COPTER TACAN' \
    | grep -v ',"IAP-HI-' | grep -v '(RNP)' | grep -v ' VISUAL ' \
    | grep -v 'SA CAT I' | grep -v 'CAT II'
