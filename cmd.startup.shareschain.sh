#!/bin/sh

mkdir -p ~/log
mkdir -p ~/.shareschain

if [ -e ~/.shareschain/shareschain.pid ]; then
    PID=`cat ~/.shareschain/shareschain.pid`
    ps -p $PID > /dev/null
    STATUS=$?
    if [ $STATUS -eq 0 ]; then
        echo "shareschain server already running"
        exit 1
    fi
fi

nohup java -jar target/shareschain-1.0-SNAPSHOT.jar > ~/log/shareschain.log 2>&1 &
echo $! > ~/.shareschain/shareschain.pid
PID=`cat ~/.shareschain/shareschain.pid`
ps -up $PID

echo "shareschain server is running now"
echo "service url http://127.0.0.1:8888"
echo "log file is ~/log/shareschain.log"
exit 0

