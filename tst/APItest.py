# -*- coding: utf-8 -*-

import Forging
import Block
import Transaction

"""
实现测试用例：
使用系统默认账户dYGL63FgMnS2UYqhPMKn9BHyCPp，密钥：space muscle beyond real grown everywhere mumble glow ash replace nightmare howl
1、启动锻造；
2、发起交易
3、等待块生成
4、结束锻造
"""
secretPhrase='space muscle beyond real grown everywhere mumble glow ash replace nightmare howl'
ip='127.0.0.1'

def Starttest():

    Forging.secretPhrase=secretPhrase
    Forging.ip = ip

    Transaction.secretPhrase=secretPhrase
    Transaction.ip=ip

    Block.ip=ip

    blockchianStatus = Block.getBlockchainStatus()
    message ='''当前区块状态：
    区块链是否在下载区块：%s;
    '''%(blockchianStatus['isDownloading'])

    print(message)
    if blockchianStatus['isDownloading']:
        print('当前节点正在下载区块，无进行交易！')
        return


    Forging.startForging()

    lastblock= Block.getBlock()
    height=lastblock['height']
    print('''区块高度：%s'''%height)

    Transaction.sendMoney()
    i = -1;
    while height == Block.getBlock()['height']:
        i += 1
        if i % 5000 == 0:
            print('正在生成区块，请等待...')

    print('新的块已生成！')

    Forging.stopForging()


if __name__ == '__main__':
    Starttest()