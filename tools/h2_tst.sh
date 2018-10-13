#!/bin/sh
java -cp ./h2*.jar org.h2.tools.Shell -url jdbc:h2:../database/tst/shareschain -user sa -password sa
