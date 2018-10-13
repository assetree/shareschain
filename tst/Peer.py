# -*- coding: utf-8 -*-

from util import req

ip=''

def getNodes():
    url = "http://"+ip+":8888/shareschain?requestType=getNodes"
    data = {}
    obj = req.post(url, data)
    return obj

def getNode(nodeIP):
    url = "http://"+ip+":8888/shareschain?requestType=getNode"
    data = {'node':nodeIP}
    obj = req.post(url, data)
    return obj

def addNode(nodeIP):
    url = "http://"+ip+":8888/shareschain?requestType=addNode"
    data = {'node':nodeIP}
    obj = req.post(url, data)
    return obj

if __name__ == '__main__':
    ip='127.0.0.1'
    print(getNodes())
    print(getNode('192.168.1.114'))
    print(addNode('192.168.1.114'))
