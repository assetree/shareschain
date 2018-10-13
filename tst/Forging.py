# -*- coding: utf-8 -*-

from util import req

secretPhrase = ''
ip=''

"""
启动锻造器
"""
def startForging():
    url = 'http://'+ip+':8888/shareschain?requestType=startForging'
    data = {"secretPhrase":secretPhrase}
    obj = req.post(url, data)

    if obj['hitTime'] != '':
        print('启动锻造器成功！')
    else:
        print('启动锻造器失败！')


"""
停止锻造器
"""
def stopForging():
    url = 'http://'+ip+':8888/shareschain?requestType=stopForging'
    data = {"secretPhrase": secretPhrase}
    obj = req.post(url, data)
    if obj['foundAndStopped']:
        print('结束锻造器成功！')
    else:
        findobj = findForgingBySecrePhrase();
        if findobj['errorCode'] == 5:
            print('该账户未启动锻造器，或锻造器已停止！')
        else:
            print('结束锻造其失败！')


"""
根据账户密钥查询锻造器
"""
def findForgingBySecrePhrase():
    url = 'http://'+ip+':8888/shareschain?requestType=getForging'
    data = {"secretPhrase": secretPhrase}
    obj = req.post(url, data)
    return obj


def getNextBlockGenerators():
    url = 'http://'+ip+':8888/shareschain?requestType=getNextBlockGenerators'
    data = {}
    obj = req.post(url, data)
    return obj

if __name__ == '__main__':
    secretPhrase = 'core reason make hug image sanctuary cigarette mirror rabbit proud clock visit'
    ip='127.0.0.1'
    startForging()
    print(getNextBlockGenerators())
    stopForging()
    print(findForgingBySecrePhrase())