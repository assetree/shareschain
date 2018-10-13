# -*- coding: utf-8 -*-

from util import req

ip=''

def getBlockchainStatus():
    url = 'http://'+ip+':8888/shareschain?requestType=getBlockchainStatus'
    data = {}
    obj = req.post(url, data)
    return obj

def getBlock():
    url = 'http://'+ip+':8888/shareschain?requestType=getBlock'
    data = {}
    obj = req.post(url, data)
    return obj

if __name__ == '__main__':
    ip='127.0.0.1'
    print(getBlockchainStatus())
    print(getBlock())