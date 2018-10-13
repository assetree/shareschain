# -*- coding: utf-8 -*-

from util import req

secretPhrase = ''
ip=''

"""
发起交易
"""
def sendMoney():
    url = "http://"+ip+":8888/shareschain?requestType=sendMoney"
    data = {'chain':'SCTK',
            'amountKER':1000000000,
            'recipient':'dYGNW7A8GMoHqcbkH4n3xmVjFy2',
            'secretPhrase':secretPhrase}
    obj = req.post(url, data)
    print("发起交易：")
    print(obj)
    return obj

def getUnconfirmedTransactions():
    url = "http://"+ip+":8888/shareschain?requestType=getUnconfirmedTransactions"
    data = {}
    obj = req.post(url, data)
    return obj

def getSmcTransaction(transaction,fullHash):
    url = "http://"+ip+":8888/shareschain?requestType=getSmcTransaction"
    data = {'transaction':transaction,'fullHash':fullHash}
    obj = req.post(url, data)
    return obj

def getUnconfirmedTransactionIds():
    url = "http://"+ip+":8888/shareschain?requestType=getUnconfirmedTransactionIds"
    data = {}
    obj = req.post(url, data)
    return obj

def getTransaction(fullHash):
    url = "http://"+ip+":8888/shareschain?requestType=getTransaction"
    data = {'chain':'SCTK','fullHash':fullHash}
    obj = req.post(url, data)
    return obj

def getBlockchainTransactions(account):
    url = "http://"+ip+":8888/shareschain?requestType=getTransaction"
    data = {'chain': 'SCTK', 'account': account}
    obj = req.post(url, data)
    return obj


if __name__ == '__main__':
    secretPhrase = 'space muscle beyond real grown everywhere mumble glow ash replace nightmare howl'
    ip='127.0.0.1'
    print(sendMoney())
    print(getUnconfirmedTransactions())
    print(getSmcTransaction('',''))
    print(getUnconfirmedTransactionIds())
    print(getTransaction('5108f1cb09e3e8eb66e2fd996f1b6d60d88e7e3a4e0576a3f3ff07e7be36c2dd'))