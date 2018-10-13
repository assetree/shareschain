[toc]
#Accounts
##1、getAccountId
给定一个密码口令或公钥获取帐户ID。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getAccountId
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| secretPhrase | 账户密钥 | String | 否 |  |
| publicKey | 账户公钥 | String | 否 |  |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| accountRS | 账户登录地址 | string |  |
| publicKey | 账户公钥 | string |  |
| account | 账户数字编号 | string |  |
| requestProcessingTime | 请求处理时间 | int |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "secretPhrase=space muscle beyond real grown everywhere mumble glow ash replace nightmare howl" "http://localhost:8888/shareschain?requestType=getAccountId"
```
返回值：
```
{"accountRS":"dYGL63FgMnS2UYqhPMKn9BHyCPp",
"publicKey":"8f1b72b5f3eb70961868df0d6e087632460878c9e2328751b4fa79c73d591c36",
"requestProcessingTime":0,
"account":"10357279321739221569"}
```
##2、getBalances
根据账户ID与子链名称获取账户余额信息。支持POST与GET请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getBalances
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| chain | 子链名称 | String | 是 | 与其它两个同名参数至少填写一个 |
| chain | 子链名称 | String | 是 | 与其它两个同名参数至少填写一个 |
| chain | 子链名称 | String | 是 | 与其它两个同名参数至少填写一个 |
| account | 账户数字编号或账户登录ID | String | 是 |  |
| height | 块高度 | int | 否 |  |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| balances | 账户余额列表 | string |  |
| unconfirmedBalanceKER | 账户未确认的余额 | string |  |
| balanceKER | 账户总余额 | string |  |
| requestProcessingTime | 请求处理时间 | int |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "account=dYGL63FgMnS2UYqhPMKn9BHyCPp&chain=1" "http://localhost:8888/shareschain?requestType=getBalances"
```
返回值：
```
{"balances":
	{"1":
		{"unconfirmedBalanceKER":"44652546700000000",
		 "balanceKER":"44652546700000000"}
	},
"requestProcessingTime":2}
```
##3、getAccountPublicKey
根据账户ID获取账户的公钥信息。支持POST与GET请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getAccountPublicKey
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| account | 账户数字编号或账户登录ID | String | 是 |  |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| publicKey | 账户公钥 | string |  |
| requestProcessingTime | 请求处理时间 | int |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "account=dYGL63FgMnS2UYqhPMKn9BHyCPp" "http://localhost:8888/shareschain?requestType=getAccountPublicKey"
```
返回值：
```
{
    "publicKey": "8f1b72b5f3eb70961868df0d6e087632460878c9e2328751b4fa79c73d591c36",
    "requestProcessingTime": 0
}
```
##4、getAccountLedger
获取一个或多个账户的账本日志信息列表。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getAccountLedger
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| account | 账户地址 | String | 否 | 当账户地址为空时,查询的是所有账户的账本信息 |
| firstIndex | 开始位置 | int | 否 | 可分页查询 |
| lastIndex | 结束位置 | int | 否 | 可分页查询 |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| holdingTypeIsUnconfirmed | 类型是否是未确认类型 | boolean |  |
| chain | 链的id | int |  |
| holdingType | 持有类型 | string |  |
| holdingTypeCode | 持有类型对应的code编码 | int |  |
| change | 对应的holdingType类型改变的金额 | int | 负数减少  正数增加 |
| eventType | 事件类型 | String |  |
| ledgerId | 账本id | boolean |  |
| eventHash | 事件的hash值 | String |  |
| isTransactionEvent | 是否是交易事件 | boolean |  |
| balance | 当前余额 | Long |  |
| accountRS | 账本对应的账户地址 | String |  |
| block | 账本日志产生的区块id | long |  |
| event | 事件id | long |  |
| account | 账户数字id | long |  |
| height | 事件发生的高度 | int |  |
| requestProcessingTime | 请求处理时间 | int | 以秒为单位 |

#####示例
使用curl命令模拟http请求。
```
curl -d "" "http://localhost:8888/shareschain?requestType=getAccountLedger"
```
返回值：
```
{
 "entries": [{
   "holdingTypeIsUnconfirmed": true,
   "chain": 2,
   "change": "-1000000",
   "holdingTypeCode": 1,
   "eventType": "TRANSACTION_FEE",
   "ledgerId": "186",
   "eventHash": "aeff8f05ff04f087f4139db83c4bccb56bd8119eacd3c9d0950a0f3dd94e5f78",
   "holding": "2",
   "isTransactionEvent": true,
   "balance": "18838175000",
   "holdingType": "UNCONFIRMED_COIN_BALANCE",
   "accountRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
   "block": "15257811504202190650",
   "event": "9795334682887323566",
   "account": "10250740066129332367",
   "height": 2255,
   "timestamp": 23269183
  },
  {
   "holdingTypeIsUnconfirmed": true,
   "chain": 2,
   "change": "-1000000",
   "holdingTypeCode": 1,
   "eventType": "TRANSACTION_FEE",
   "ledgerId": "185",
   "eventHash": "2ff96fa99ad39e708f0b79a06a91fd91ab8fdca94d302df03b5ce6accb7176ef",
   "holding": "2",
   "isTransactionEvent": true,
   "balance": "18839175000",
   "holdingType": "UNCONFIRMED_COIN_BALANCE",
   "accountRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
   "block": "15257811504202190650",
   "event": "8115156239789324591",
   "account": "10250740066129332367",
   "height": 2255,
   "timestamp": 23269183
  }
 ],
 "requestProcessingTime": 1
}
```
#Blocks
##1、getBlockchainStatus
获取当前区块链状态信息。支持POST与GET请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getBlockchainStatus
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| 无 |


#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| apiProxy | 是否为API代理 | boolean |  |
| correctInvalidFees | 是否验证无效费用 | boolean |  |
| ledgerTrimKeep | 整理区块时保留的块个数 | int |  |
| maxAPIRecords | 最大的API数量 | int |  |
| blockchainState | 当前区块链的状态 | String |  |
| currentMinRollbackHeight | 当前最小回滚高度 | int |  |
| numberOfBlocks | 块总的数量 | int |  |
| isTestnet | 是否测试网络 | boolean |  |
| includeExpiredPrunable | 是否包含失效的交易信息 | boolean |  |
| isLightClient | 是否为轻客户端 | boolean |  |
| services | 当前启动的服务 | list |  |
| version | 区块链版本 | String |  |
| maxRollback | 最大回滚区块数 | int |  |
| lastBlock | 最后一个块编号 | String |  |
| application | 应用平台名称 | String |  |
| isScanning | 是否正在扫描 | boolean |  |
| isDownloading | 是否正在下载 | boolean |  |
| cumulativeDifficulty | 最后一个块的累积困难度 | String |  |
| lastBlockchainFeederHeight | 最后一个块锻造者的开始锻造的高度 | int |  |
| maxPrunableLifetime | 最多生命周期 | Long |  |
| time | 最后一个块时间戳 | Long |  |
| lastBlockchainFeeder | 最后一个块锻造者IP地址 | String |  |
| requestProcessingTime | 请求返回时间 | int |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "" "http://localhost:8888/shareschain?requestType=getBlockchainStatus"
```
返回值：
```
{
    "apiProxy": false,
    "correctInvalidFees": false,
    "ledgerTrimKeep": 30000,
    "maxAPIRecords": 100,
    "blockchainState": "UP_TO_DATE",
    "currentMinRollbackHeight": 1557,
    "numberOfBlocks": 2358,
    "isTestnet": false,
    "includeExpiredPrunable": true,
    "isLightClient": false,
    "services": [
        "CORS"
    ],
    "requestProcessingTime": 0,
    "version": "2.0.14",
    "maxRollback": 800,
    "lastBlock": "8442703785293069224",
    "application": "Shareschain",
    "isScanning": false,
    "isDownloading": false,
    "cumulativeDifficulty": "395346139519243",
    "lastBlockchainFeederHeight": 2266,
    "maxPrunableLifetime": 7776000,
    "time": 23275025,
    "lastBlockchainFeeder": "192.168.1.113"
}
```
##2、getBlock
根据条件查询，显示块信息，如果条件都为空，显示当前链最后一个块信息。支持POST与GET请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getBlock
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| block | 块编号 | String | 否 |  |
| height | 块高度 | String | 否 |  |
| timestamp | 时间戳 | String | 否 |  |
| includeTransactions | 是否包含交易信息 | boolean | 否 | 参数为“true”时，显示交易信息 |
| includeExecutedPhased | 是否包含投票信息 | boolean | 否 | 参数为“true”时，显示投票信息 |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| previousBlockHash | 前一块Hash值 | String |  |
| generationSignature | 块锻造者签名 | String |  |
| generator | 块锻造者数字编号 | String |  |
| generatorPublicKey | 块锻造者公钥 | String |  |
| baseTarget | 基础目标值 | String |  |
| payloadHash | 所有交易对象的Hash值 | String |  |
| generatorRS | 块锻造者登录ID | String |  |
| nextBlock | 下一块编号 | String |  |
| requestProcessingTime | 请求返回时间 | int |  |
| numberOfTransactions | 块中交易总个数 | int |  |
| blockSignature | 块签名 | String |  |
| transactions | 交易列表 | List |  |
| version | 块版本 | int |  |
| previousBlock | 前一块编号 | String |  |
| cumulativeDifficulty | 累积困难值 | String |  |
| totalFeeKER | 总交易数额 | Long |  |
| block | 当前块编号 | String |  |
| height | 块高度 | int |  |
| timestamp | 块时间戳 | Long |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "" "http://localhost:8888/shareschain?requestType=getBlock"
```
返回值：
```
{
    "previousBlockHash": "bbfc10dad7c5d69dbfe7ccd1cdea56a51420823989edbf74a3e25ac244cf6b87",
    "generationSignature": "735aa129b011118188bedae0f613921c2a51ae7e94568f8cd7fd70635fd66254",
    "generator": "3772554820164523914",
    "generatorPublicKey": "0affd5d91bd4da0c684a4d622d15e69b157223b3670790bc470db0d6ce6c9558",
    "baseTarget": "454375187",
    "payloadHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "generatorRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
    "nextBlock": "13543989766432644394",
    "requestProcessingTime": 0,
    "numberOfTransactions": 0,
    "blockSignature": "bdde57a1a731d9c39f2fc55f808015c44d3c01df0b255d7c86973b29d7a0360c808c6778d783db1fc644708123996eb5409f106f93c6874e0339c7ce3dcf7f85",
    "transactions": [],
    "version": 3,
    "previousBlock": "11373495439837953211",
    "cumulativeDifficulty": "385387679223850",
    "totalFeeKER": "0",
    "block": "3452244155391142283",
    "height": 2271,
    "timestamp": 23270116
}
```



#Forging
##1、startForging
启动账户的锻造功能,当节点账号满足一定的条件(如:有效余额大于1000,有效余额经过一定数量的区块确认等)时,可以启动锻造功能,当节点成功锻造一个区块时,会得到相应的交易费用作为奖励。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=startForging
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| secretPhrase | 账户密码 | String | 是 | |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| deadline | 最后期限 | int | 从上一个块开始算起以秒为单位，直到该帐户将锻造一个区块为止 |
| hitTime | 碰撞的时间 | long | 以生成区块之后的秒为单位,该帐号将锻造一个区块 |
| requestProcessingTime | 请求处理时间 | int | 以秒为单位 |

#####示例
使用curl命令模拟http请求。
```
curl -d "secretPhrase=core reason make hug image sanctuary cigarette mirror rabbit proud clock visit" "http://localhost:8888/shareschain?requestType=startForging"
```
返回值：
```
{
 "requestProcessingTime": 2,
 "deadline": 27,
 "hitTime": 23183937
}
```

##2、stopForging
停止账户的锻造功能。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=stopForging
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| secretPhrase | 账户密码 | String | 是 | |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| foundAndStopped | 锻造器是否停止 | boolean | true 表示锻造器停止成功  false表示锻造器已经停止了 |
| forgersCount | 锻造区块的数量 | int |  |
| requestProcessingTime | 请求处理时间 | int | 以秒为单位 |

#####示例
使用curl命令模拟http请求。
```
curl -d "secretPhrase=core reason make hug image sanctuary cigarette mirror rabbit proud clock visit" "http://localhost:8888/shareschain?requestType=stopForging"
```
返回值：
```
{
 "foundAndStopped": true,
 "forgersCount": 0,
 "requestProcessingTime": 1
}
```

##3、getNextBlockGenerators
返回下一个区块生成器的列表。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getNextBlockGenerators
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| limit | 限制数量 | int | 否 | 限制显示区块生成器的数量 |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| activeCount | 激活锻造账户的数量 | int |  |
| lastBlock | 最后区块的id | long |  |
| accountRS | 生成器账户 | String |  |
| effectiveBalanceSCTK | 有效余额 | long |  |
| deadline | 失效时间 | int | 单位时间分钟 |
| account | 账户的数字id | long |  |
| hitTime | 下次生成区块的时间戳 | long |  |
| height | 当前高度| int |  |
| requestProcessingTime | 请求处理时间 | int | 以秒为单位 |

#####示例
使用curl命令模拟http请求。
```
curl -d "limit=1" "http://localhost:8888/shareschain?requestType=getNextBlockGenerators"
```
返回值：
```
{
 "activeCount": 4,
 "lastBlock": "7971222005441241472",
 "generators": [{
  "accountRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
  "effectiveBalanceSCTK": 446527354,
  "deadline": 21,
  "account": "10250740066129332367",
  "hitTime": 23358344
 }],
 "requestProcessingTime": 33,
 "timestamp": 23358323,
 "height": 2507
}
```

##4、getForging
查询账户是否在锻造中。支持POST与GET请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getForging
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| secretPhrase | 账户密码 | String | 否 |  |
| adminPassword | 管理员账户密钥 | String | 否 |  |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| generators | 锻造者列表 | List |  |
| accountRS | 锻造者账户登录ID | String |  |
| deadline | 锻造期限 | int |  |
| account | 锻造者账户数字编号 | String |  |
| remaining | 块生成剩余时间 | int |  |
| hitTime | 块生成时间戳 | int |  |
| requestProcessingTime | 请求处理时间 | int |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "" "http://localhost:8888/shareschain?requestType=getForging"
```
返回值：
```
{
    "generators": [
        {
            "accountRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
            "deadline": 114,
            "account": "10357279321739221569",
            "remaining": 50,
            "hitTime": 23356714
        }
    ],
    "requestProcessingTime": 0
}
```
#Networking

##1、getNodes
获取节点列表信息及ip地址。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getNodes
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| active | 节点是否是激活的 | boolean | 否 |  |
| state | 节点状态  | String | 否 | 三个值可选:NON_CONNECTED, CONNECTED, or DISCONNECTED |
| includeNodeInfo | 是否包含节点信息 | boolean | 否 | true表示包含 false不包含,只返回节点的ip地址 |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| downloadedVolume | 已下载的字节数 | long |  |
| address | 节点地址 | String |  |
| inbound | 是否一个入站的链接 | boolean | |
| blockchainState | 区块链的状态 | String | |
| uploadedVolume | 已上传的字节数 | long | |
| services | 节点启动的服务名称 | list | |
| version | 应用版本号 | String | |
| platform | 应用所在平台（操作系统） | String | |
| lastUpdated | 最后更新时间戳 | long | |
| blacklisted | 是否被列入黑名单列表 | boolean | |
| application | 应用名称 | String | |
| announcedAddress | 对外公布的地址 | String | |
| port | 端口号 | int | |
| lastConnectAttempt | 最后一次链接的时间戳 | boolean | |
| shareAddress | 是否共享地址 | boolean | |


#####示例
使用curl命令模拟http请求。
```
curl -d "active=true&includeNodeInfo=true" "http://localhost:8888/shareschain?requestType=getNodes"
```
返回值：
```
{
 "nodes": [{
   "downloadedVolume": 2560,
   "address": "192.168.1.114",
   "inbound": true,
   "blockchainState": "DOWNLOADING",
   "uploadedVolume": 2424,
   "services": [
    "CORS"
   ],
   "version": "2.0.14",
   "platform": "Linux amd64",
   "lastUpdated": 23352016,
   "blacklisted": false,
   "announcedAddress": "192.168.1.114",
   "application": "Shareschain",
   "port": 27874,
   "lastConnectAttempt": 23333118,
   "state": 1,
   "shareAddress": true
  },
  {
   "downloadedVolume": 0,
   "address": "192.168.1.116",
   "inbound": false,
   "blockchainState": "UP_TO_DATE",
   "uploadedVolume": 0,
   "services": [],
   "version": null,
   "platform": null,
   "lastUpdated": 23347781,
   "blacklisted": false,
   "announcedAddress": "192.168.1.116",
   "application": null,
   "port": 27874,
   "lastConnectAttempt": 23351471,
   "state": 2,
   "shareAddress": true
  }
 ],
 "requestProcessingTime": 1
}
```

##2、getNode
根据ip地址获取节点信息。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getNode
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| node | 节点地址 | String | 是 |  |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| downloadedVolume | 已下载的字节数 | long |  |
| address | 节点地址 | String |  |
| inbound | 是否一个入站的链接 | boolean | |
| blockchainState | 区块链的状态 | String | |
| uploadedVolume | 已上传的字节数 | long | |
| services | 节点启动的服务名称 | list | |
| version | 应用版本号 | String | |
| platform | 应用所在平台（操作系统） | String | |
| lastUpdated | 最后更新时间戳 | long | |
| blacklisted | 是否被列入黑名单列表 | boolean | |
| application | 应用名称 | String | |
| announcedAddress | 对外公布的地址 | String | |
| port | 端口号 | int | |
| lastConnectAttempt | 最后一次链接的时间戳 | boolean | |
| shareAddress | 是否共享地址 | boolean | |


#####示例
使用curl命令模拟http请求。
```
curl -d "node=192.168.1.114" "http://localhost:8888/shareschain?requestType=getNode"
```
返回值：
```
{
 "downloadedVolume": 6517,
 "address": "192.168.1.114",
 "inbound": true,
 "blockchainState": "DOWNLOADING",
 "uploadedVolume": 6490,
 "services": [
  "CORS"
 ],
 "requestProcessingTime": 0,
 "version": "2.0.14",
 "platform": "Linux amd64",
 "lastUpdated": 23356105,
 "blacklisted": false,
 "announcedAddress": "192.168.1.114",
 "application": "Shareschain",
 "port": 27874,
 "lastConnectAttempt": 23333118,
 "state": 1,
 "shareAddress": true
}
```
##3、addNode
将节点添加到已知节点的列表中，并尝试连接该节点。返回节点链接信息。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=addNode
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| node | IP地址或域名 | String | 是 | 可以添加配置端口 |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| downloadedVolume | 已下载的字节数 | long |  |
| address | 节点IP地址 | String |  |
| inbound | 入站节点 | boolean |  |
| blockchainState | 节点上区块链的状态 | String |  |
| uploadedVolume | 已更新的字节数大小 | long |  |
| requestProcessingTime | 请求处理时间 | int |  |
| version | 应用版本 | int |  |
| platform | 应用所在平台（操作系统） | String |  |
| lastUpdated | 最后一次更新时间戳 | int |  |
| blacklisted | 是否在黑名单中 | boolean | false：不在黑名单中;true：在黑名单中 |
| announcedAddress | 对外公布的地址 | String |  |
| apiPort | 对外公布的API端口号 | int | 如果对外公布API，则显示此项 |
| application | 应用名称 | String |  |
| port | 节点接口 | int |  |
| lastConnectAttempt | 最后一次链接时间戳 | int |  |
| state | 节点的状态 | int | 0：NON_CONNECTED;1：CONNECTED;2：DISCONNECTED |
| isNewlyAdded | 是否新添加的节点 | boolean |  |
| shareAddress | 是否分享地址 | boolean |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "node=192.168.1.149" "http://localhost:8888/shareschain?requestType=addNode"
```
返回值：
```
{"downloadedVolume":1044,
"address":"192.168.1.149",
"inbound":false,
"blockchainState":"DOWNLOADING",
"uploadedVolume":412,
"services":["API","CORS"],
"requestProcessingTime":1,
"version":"2.0.14",
"platform":"Linuxarm",
"lastUpdated":22737988,
"blacklisted":false,
"announcedAddress":"192.168.1.149",
"apiPort":8888,
"application":"Shareschain",
"port":27874,
"lastConnectAttempt":22737883,
"state":1,
"isNewlyAdded":false,
"shareAddress":true}
```



#Tranasctions
##1、sendMoney
发送一笔金额到某个特定账户。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=sendMoney
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| chain | 链的id | int | 是 |  |
| amountKER | 转账金额 | int | 是 |  |
| recipient | 接收人账户地址 | String | 是 |  |
| secretPhrase | 发送者账户密码 | String | 是 | |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| minimumFeeKER | 系统计算的最低交易费用 | String |  |
| signatureHash | 交易的签名hash值 | String | 通过交易签名计算获得 |
| senderPublicKey | 发送者的公钥 | String | 通过发送者的密码计算获得 |
| chain | 链的id | int | |
| signature | 交易的签名 | String | |
| feeKER | 交易费用 | String | |
| type | 交易类型 | int | |
| fullHash | 交易的fullHash值 | String |  |
| version | 交易的版本号| int |  |
| smcTransaction | 主链交易 | String |  |
| phased | 是否审批 | boolean |  默认false|
| ecBlockId | 已知最后的区块id | String |  |
| version.SmcPayment | 主链交易的支付版本 | int |  |
| subtype | 交易的子类型 | int |  |
| amountKER | 转账金额 | int | 资产交易时,该值为0 |
| sender | 发送者数字id | String |  |
| recipientRS | 接收账户地址 | String |  |
| recipient | 接收账户数字id | String |  |
| ecBlockHeight | 已知最后区块的高度 | int |  |
| deadline | 交易有效时间 | int | 单位分钟 |
| transaction | 交易id | Long | 根据交易的fullHash计算得出 |
| timestamp | 交易时间戳 | String |  |
| height | 当前高度 | long |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "chain=1&amountKER=1&recipient=dYGL63FgMnS2UYqhPMKn9BHyCPp&secretPhrase=space muscle beyond real grown everywhere mumble glow ash replace nightmare howl" "http://localhost:8888/shareschain?requestType=sendMoney"
```
返回值：
```
{
 "minimumFeeKER": "100000000",
 "signatureHash": "317805377977b692e503ed1af03873ca550dd0d08c7587874a8f0aacf365c526",
 "transactionJSON": {
  "senderPublicKey": "5a539744e0158758640b05a7c26945acc8605bf793972bb2bbc28995ce96635a",
  "chain": 1,
  "signature": "35278c86a38a5533dcf3b3d855304a2ce7a51f055bbb291013d0a833204bf90049b4ee56392035882c1a5d340f511fc39c28f308d3667161de3d8c1e757fb0fc",
  "feeKER": "100000000",
  "type": -2,
  "fullHash": "d3ad676b6e8533d37792e9d140ed7f6adce8954ef023c21d4977cece2b5600fd",
  "version": 1,
  "phased": false,
  "ecBlockId": "11314476582458661305",
  "signatureHash": "317805377977b692e503ed1af03873ca550dd0d08c7587874a8f0aacf365c526",
  "attachment": {
   "version.SmcPayment": 0
  },
  "senderRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
  "subtype": 0,
  "amountKER": "1",
  "sender": "10250740066129332367",
  "recipientRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
  "recipient": "11545536034630234291",
  "ecBlockHeight": 1423,
  "deadline": 15,
  "transaction": "15218654275109891539",
  "timestamp": 23253480,
  "height": 2147483647
 },
 "unsignedTransactionBytes": "01000000fe0001e8d162010f005a539744e0158758640b05a7c26945acc8605bf793972bb2bbc28995ce96635ab3709b45dafb39a0010000000000000000e1f50500000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008f050000b999ec4c8118059d00000000",
 "broadcasted": true,
 "requestProcessingTime": 8,
 "transactionBytes": "01000000fe0001e8d162010f005a539744e0158758640b05a7c26945acc8605bf793972bb2bbc28995ce96635ab3709b45dafb39a0010000000000000000e1f5050000000035278c86a38a5533dcf3b3d855304a2ce7a51f055bbb291013d0a833204bf90049b4ee56392035882c1a5d340f511fc39c28f308d3667161de3d8c1e757fb0fc8f050000b999ec4c8118059d00000000",
 "fullHash": "d3ad676b6e8533d37792e9d140ed7f6adce8954ef023c21d4977cece2b5600fd"
}
```

##2、getUnconfirmedTransactions
获取账户的未确认的交易列表。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getUnconfirmedTransactions
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| chain | 链的id | int | 否 |  |
| account | 账户地址 | String | 否 |  |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| signatureHash | 交易的签名hash值 | String | 通过交易签名计算获得 |
| senderPublicKey | 发送者的公钥 | String | 通过发送者的密码计算获得 |
| chain | 链的id | int | |
| signature | 交易的签名 | String | |
| feeKER | 交易费用 | String | |
| type | 交易类型 | int | |
| fullHash | 交易的fullHash值 | String |  |
| version | 交易的版本号| int |  |
| smcTransaction | 主链交易 | String |  |
| phased | 是否审批 | boolean |  默认false|
| ecBlockId | 已知最后的区块id | String |  |
| version.SmcPayment | 主链交易的支付版本 | int |  |
| subtype | 交易的子类型 | int |  |
| amountKER | 转账金额 | int |  |
| sender | 发送者数字id | String |  |
| recipientRS | 接收账户地址 | String |  |
| recipient | 接收账户数字id | String |  |
| ecBlockHeight | 已知最后区块的高度 | int |  |
| deadline | 交易有效时间 | int | 单位分钟 |
| transaction | 交易id | Long | 根据交易的fullHash计算得出 |
| timestamp | 交易时间戳 | String |  |
| height | 当前高度 | long |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "chain=1&account=dYGL63FgMnS2UYqhPMKn9BHyCPp" "http://localhost:8888/shareschain?requestType=getUnconfirmedTransactions"
```
返回值：
```
{
 "unconfirmedTransactions": [{
  "senderPublicKey": "5a539744e0158758640b05a7c26945acc8605bf793972bb2bbc28995ce96635a",
  "chain": 1,
  "signature": "8c1de083d9c5d5c699e8c6d4fdb09a73ce7dadf98222fe9ac639dbbfd9556c09f31f47b8b271a1e6c7a0169b629e9f724b61682989a67239d0263e73821e4f7b",
  "feeKER": "100000000",
  "type": -2,
  "fullHash": "650da413688fddc6a6da8bf25813955af89a245ea4b2bf3c59f572a8add83818",
  "version": 1,
  "phased": false,
  "ecBlockId": "4044184707597471031",
  "signatureHash": "9fb540a5330f391a7d62aca51dd8a2c6d7c75b74b9d5abc716a2d472d19eba22",
  "attachment": {
   "version.SmcPayment": 0
  },
  "senderRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
  "subtype": 0,
  "amountKER": "100000000",
  "sender": "10250740066129332367",
  "recipientRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
  "recipient": "11545536034630234291",
  "ecBlockHeight": 1758,
  "deadline": 15,
  "transaction": "14329767266531675493",
  "timestamp": 23285962,
  "height": 2147483647
 }],
 "requestProcessingTime": 1
}
```

##3、getSmcTransaction
获取预期在下一个块中执行并且删除的资产列表。支持POST与GET请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getSmcTransaction
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| transaction | 交易编号 | String | 是 | 该参数与fullHash参数至少选择一个 |
| fullHash | 交易的fullHash值 | String | 是 | 该参数与transaction参数至少选择一个 |
| includeChildTransactions | 是否包含子链交易 | String | 否 |  |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| signatureHash | 交易的签名hash值 | String | 通过交易签名计算获得 |
| senderPublicKey | 发送者的公钥 | String | 通过发送者的密码计算获得 |
| chain | 链的id | int | |
| signature | 交易的签名 | String | |
| feeKER | 交易费用 | String | |
| type | 交易类型 | int | |
| fullHash | 交易的fullHash值 | String | |
| version | 交易的版本号| int | |
| smcTransaction | 主链交易 | String | 子链的交易会被绑定到主链上 |
| phased | 是否审批 | boolean | 默认false|
| ecBlockId | 已知最后的区块id | long | |
| version.Message | 交易的版本号 | int | |
| messageIsText | 交易是否是文本 | boolean | |
| message | 发送的消息内容 | String | |
| subtype | 交易的子类型 | int | |
| amountKER | 转账金额 | int | 资产交易时,该值为0 |
| sender | 发送者数字id | String | |
| recipientRS | 接收账户地址 | String | |
| recipient | 接收账户数字id | String | |
| ecBlockHeight | 已知最后区块的高度 | int | |
| deadline | 交易有效时间 | int | 单位分钟 |
| transaction | 交易id | Long | 根据交易的fullHash计算得出 |
| timestamp | 交易时间戳 | String | |
| height | 当前高度 | long | |
| confirmations | 已经确认的高度 | int | |
| block | 打包该交易的区块id | long | |
| blockTimestamp | 区块生成的时间戳 | long | |
#####示例
使用curl命令模拟http请求。
```
curl -d "transaction=5668139040557482739" "http://localhost:8888/shareschain?requestType=getSmcTransaction"
```
返回值：
```
{
    "signature": "3688a6d0e65227f4eea608b4c47c22abefa3bebd99e6c8a459e5eec64a37cc02dac7b16cbcc4051dae0647361da2c36e206aafaef33e295a29b246e359bf5675",
    "transactionIndex": 0,
    "type": -2,
    "phased": false,
    "ecBlockId": "9229891090904923357",
    "signatureHash": "6c2264c332d56807715428dd54c4c4c926e97e06965fda5b11233c271289fdd8",
    "attachment": {
        "version.SmcPayment": 0
    },
    "senderRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
    "subtype": 0,
    "amountKER": "100000000",
    "recipientRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
    "block": "6805374665581282216",
    "blockTimestamp": 21472003,
    "deadline": 15,
    "timestamp": 21471898,
    "height": 473,
    "senderPublicKey": "5a539744e0158758640b05a7c26945acc8605bf793972bb2bbc28995ce96635a",
    "chain": 1,
    "feeKER": "100000000",
    "requestProcessingTime": 0,
    "confirmations": 2054,
    "fullHash": "f3c2f3216d46a94e7f59d44fc142b3274f6d2576bd7422557d938fd674c1f205",
    "version": 1,
    "sender": "10250740066129332367",
    "recipient": "10357279321739221569",
    "ecBlockHeight": 0,
    "transaction": "5668139040557482739"
}
```
##4、getUnconfirmedTransactionIds
获取账户的未确认的交易id列表。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getUnconfirmedTransactionIds
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| chain | 链的id | int | 否 |  |
| account | 账户地址 | String | 否 |  |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| unconfirmedTransactionIds | 未确认交易id列表 | list |  |
| requestProcessingTime | 请求处理时间 | int |  |


#####示例
使用curl命令模拟http请求。
```
curl -d "chain=1&account=dYGL63FgMnS2UYqhPMKn9BHyCPp" "http://localhost:8888/shareschain?requestType=getUnconfirmedTransactionIds"
```
返回值：
```
{
 "requestProcessingTime": 1,
 "unconfirmedTransactionIds": [
  "14329767266531675493"
 ]
}
```

##5、getTransaction
根据交易的fullhash值获取交易的信息。仅支持POST请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getTransaction
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| chain | 链的id | int | 是 |  |
| fullHash | 交易的fullhash值 | String | 是 |  |

#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| signatureHash | 交易的签名hash值 | String | 通过交易签名计算获得 |
| senderPublicKey | 发送者的公钥 | String | 通过发送者的密码计算获得 |
| chain | 链的id | int | |
| signature | 交易的签名 | String | |
| feeKER | 交易费用 | String | |
| type | 交易类型 | int | |
| fullHash | 交易的fullHash值 | String |  |
| version | 交易的版本号| int |  |
| smcTransaction | 主链交易 | String | 子链的交易会被绑定到主链上 |
| phased | 是否审批 | boolean |  默认false|
| ecBlockId | 已知最后的区块id | long |  |
| version.Message | 交易的版本号 | int |  |
| messageIsText | 交易是否是文本 | boolean |  |
| message | 发送的消息内容 | String |  |
| subtype | 交易的子类型 | int |  |
| amountKER | 转账金额 | int | 资产交易时,该值为0 |
| sender | 发送者数字id | String |  |
| recipientRS | 接收账户地址 | String |  |
| recipient | 接收账户数字id | String |  |
| ecBlockHeight | 已知最后区块的高度 | int |  |
| deadline | 交易有效时间 | int | 单位分钟 |
| transaction | 交易id | Long | 根据交易的fullHash计算得出 |
| timestamp | 交易时间戳 | String |  |
| height | 当前高度 | long |  |
| confirmations | 已经确认的高度 | int |  |
| block | 打包该交易的区块id | long |  |
| blockTimestamp | 区块生成的时间戳 | long |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "chain=2&fullHash=aeff8f05ff04f087f4139db83c4bccb56bd8119eacd3c9d0950a0f3dd94e5f78" "http://localhost:8888/shareschain?requestType=getTransaction"
```
返回值：
```
{
 "signature": "9b2da86c326df7ae263bf8efc33f0e9885e38ed1f5908f25261d62d1975798005a1c6d534e783cb557eed2d1742e0b39d6ceb2cc0828c6db76f2b4107ab4e456",
 "transactionIndex": 2,
 "type": 1,
 "smcTransaction": "13014964144614554597",
 "phased": false,
 "ecBlockId": "6906427969160802191",
 "signatureHash": "7a2bef2de92a4599dd88e881566f5856b870a43f89a87d2691b3afe2986e4713",
 "attachment": {
  "version.PrunablePlainMessage": 1,
  "messageIsText": true,
  "messageHash": "9e581ae0f31cf6ea6269eb4975c0316c2df06240c6465d0e6242a7a52a3b21d8",
  "message": "发送了一条明文的消息",
  "version.ArbitraryMessage": 0
 },
 "senderRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
 "subtype": 0,
 "amountKER": "0",
 "recipientRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
 "block": "15257811504202190650",
 "blockTimestamp": 23269183,
 "deadline": 15,
 "timestamp": 23269126,
 "height": 2255,
 "senderPublicKey": "5a539744e0158758640b05a7c26945acc8605bf793972bb2bbc28995ce96635a",
 "chain": 2,
 "feeKER": "1000000",
 "requestProcessingTime": 1,
 "confirmations": 223,
 "fullHash": "aeff8f05ff04f087f4139db83c4bccb56bd8119eacd3c9d0950a0f3dd94e5f78",
 "version": 1,
 "sender": "10250740066129332367",
 "recipient": "11545536034630234291",
 "ecBlockHeight": 1531
}
```
##6、getBlockchainTransactions
获取区块链中账户的交易信息列表。支持POST与GET请求。
#####接口地址
http://localhost:8888/shareschain?requestType=getBlockchainTransactions
#####参数说明
|变量名| 说明 | 类型 | 必填|备注|
| :------:| :------: | :------: | :------: | :------: |
| chain | 链的id | int | 是 |  |
| account | 账户登录ID | String | 是 |  |
| timestamp | 时间戳 | String | 否 |  |
| type | 交易类型 | int | 否 |  |
| subtype | 交易子类型 | int | 否 |  |
| firstIndex | 起始索引 | int | 否 |  |
| lastIndex | 结束索引 | int | 否 |  |
| numberOfConfirmations | 交易被确认的次数 | int | 否 |  |
| withMessage | 是否带有消息 | boolean | 否 | 参数为“true”时，查询带有消息的交易 |
| phasedOnly | 是否仅查询投票的交易 | boolean | 否 | 参数为“true”时，只查询投票的交易 |
| nonPhasedOnly | 是否仅查询非投票的交易 | boolean | 否 | 参数为“true”时，只查询非投票的交易 |
| includeExpiredPrunable | 是否包含已过期的交易 | boolean | 否 | 参数为“true”时，显示已过期的交易 |
| includePhasingResult | 是否包含投票结果 | boolean | 否 | 参数为“true"时，显示投票结果 |
| executedOnly | 是否仅显示已执行的交易 | boolean | 否 | 参数为”true“时，仅显示已执行的交易 |


#####返回参数说明
|变量名| 说明 | 类型 | 备注|
| :------:| :------: | :------: | :------: |
| transfers | 交易列表 | List |  |
| requestProcessingTime | 请求处理时间 | int |  |

#####示例
使用curl命令模拟http请求。
```
curl -d "chain=IGNIS&account=dYGL63FgMnS2UYqhPMKn9BHyCPp" "http://localhost:8888/shareschain?requestType=getBlockchainTransactions"
```
返回值：
```
{
    "requestProcessingTime": 1,
    "transactions": [
        {
            "senderPublicKey": "8f1b72b5f3eb70961868df0d6e087632460878c9e2328751b4fa79c73d591c36",
            "chain": 2,
            "signature": "9ab044a6a671543eb80639d5e81f2b3146129dadd058b9dd899ba7c25a0de103fb5db65d1a745a658b7f764a3c4df7cedbee1f5ca2578bae73dfaccb153ce74a",
            "feeKER": "5000000",
            "transactionIndex": 1,
            "type": 2,
            "confirmations": 2093,
            "fullHash": "3cd7d107830701efc8192b8e9bf4b0b38d06ef726bc69207756440652e7a09e6",
            "version": 1,
            "smcTransaction": "8412468414414032344",
            "phased": false,
            "ecBlockId": "9229891090904923357",
            "signatureHash": "dced192fb40892cf84fe8efd48445624311959278d5c451b101dba8bd8b7be42",
            "attachment": {
                "version.AssetDelete": 1,
                "asset": "15744232157019124892"
            },
            "senderRS": "dYGL63FgMnS2UYqhPMKn9BHyCPp",
            "subtype": 7,
            "amountKER": "0",
            "sender": "10357279321739221569",
            "ecBlockHeight": 0,
            "block": "13866467128351846170",
            "blockTimestamp": 20944008,
            "deadline": 15,
            "timestamp": 20943964,
            "height": 385
        }
    ]
}
```
