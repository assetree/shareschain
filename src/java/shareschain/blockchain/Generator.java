
package shareschain.blockchain;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.account.Account;
import shareschain.util.crypto.Crypto;
import shareschain.util.Convert;
import shareschain.util.Listener;
import shareschain.util.Listeners;
import shareschain.util.Logger;
import shareschain.util.ThreadPool;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class Generator implements Comparable<Generator> {

    public enum Event {
        GENERATION_DEADLINE, START_FORGING, STOP_FORGING
    }

    private static final int MAX_FORGERS = Shareschain.getIntProperty("shareschain.maxNumberOfForgers");
    private static final byte[] fakeForgingPublicKey = Shareschain.getBooleanProperty("shareschain.enableFakeForging") ?
            Convert.parseHexString(Shareschain.getStringProperty("shareschain.fakeForgingPublicKey")) : null;

    private static final Listeners<Generator,Event> listeners = new Listeners<>();

    private static final ConcurrentMap<String, Generator> generators = new ConcurrentHashMap<>();
    private static final Collection<Generator> allGenerators = Collections.unmodifiableCollection(generators.values());
    private static volatile List<Generator> sortedForgers = null;
    private static long lastBlockId;
    /**
     * 延时时间
     */
    private static int delayTime = Constants.FORGING_DELAY;

    private static final Runnable generateBlocksThread = new Runnable() {

        private volatile boolean logged;

        @Override
        public void run() {

            try {
                try {
                    final int now = Shareschain.getEpochTime();
                    if (now < 0) {
                        return;
                    }
                    /**
                     * 获取区块链读写锁
                     */
                    BlockchainImpl.getInstance().updateLock();
                    try {
                        /**
                         * 获取当前区块链最后一个区块
                         */
                        Block lastBlock = Shareschain.getBlockchain().getLastBlock();
                        /**
                         * Constants.LAST_KNOWN_BLOCK 常量，最后一个已知的区块高度，
                         * 如果当前区块链高度小于已知的区块高度，说明当前节点初始化未完成，不进行后续操作
                         */
                        if (lastBlock == null || lastBlock.getHeight() < Constants.LAST_KNOWN_BLOCK) {
                            return;
                        }

                        /**
                         * delaytime 为 10s
                         */
                        final int generationLimit = now - delayTime;
                        /**
                         * 如果从区块链中获取到的区块编号与当前记录的区块编号不一致，说明为新的区块
                         * 或者区块构造器列表为空
                         * 重新创建区块构造器
                         */
                        if (lastBlock.getId() != lastBlockId || sortedForgers == null) {
                            lastBlockId = lastBlock.getId();
                            /**
                             * 如果本节点最后一个区块的时间戳大于系统当前时间前10（60*10）分钟的时间戳，说明该区块为无效区块
                             * 并且最后一个区块不是创世区块（创世区块的PreviousBlockId的值为0）
                             *
                             * 将所有区块构造器的最后一个区块回滚到上一个区块，重新设置区块构造器
                             */
                            if (lastBlock.getTimestamp() > now - 600 && lastBlock.getPreviousBlockId() != 0) {
                                Block previousBlock = Shareschain.getBlockchain().getBlock(lastBlock.getPreviousBlockId());
                                for (Generator generator : generators.values()) {
                                    //setLastBlock(previousBlock) 重新设置generator，获得新的区块生成时间
                                    generator.setLastBlock(previousBlock);
                                    int timestamp = generator.getTimestamp(generationLimit);
                                    //如果重新设置的区块生成时间 比本节点原有的最后一个区块时间戳小，说明本节点最后一个区块为非法区块，进行回滚处理
                                    if (timestamp != generationLimit && generator.getHitTime() > 0 && timestamp < lastBlock.getTimestamp()) {
                                        Logger.logDebugMessage("Pop off: " + generator.toString() + " will pop off last block " + lastBlock.getStringId());
                                        List<BlockImpl> poppedOffBlock = BlockchainProcessorImpl.getInstance().popOffTo(previousBlock);
                                        for (BlockImpl block : poppedOffBlock) {
                                            TransactionProcessorImpl.getInstance().processLater(block.getSmcTransactions());
                                        }
                                        lastBlock = previousBlock;
                                        lastBlockId = previousBlock.getId();
                                        break;
                                    }
                                }
                            }
                            /**
                             * 选择可用的区块构造器Generator 列表，以账户余额为准，如果账户余额小于零，不作为构造器处理
                             */
                            List<Generator> forgers = new ArrayList<>();
                            // 节点的锻造器集合
                            for (Generator generator : generators.values()) {
//                                hitTime 触发计算时间为设置最新块
                                generator.setLastBlock(lastBlock);
                                if (generator.effectiveBalance.signum() > 0) {
                                    forgers.add(generator);
                                }
                            }
                            /**
                             * 对forgers（可用的区块构造器）进行降序排序
                             */
                            Collections.sort(forgers);
                            sortedForgers = Collections.unmodifiableList(forgers);
                            logged = false;
                        }
                        /**
                         * 判断是否打印日志
                         */
                        if (!logged) {
                            for (Generator generator : sortedForgers) {
                                if (generator.getHitTime() - generationLimit > 60) {
                                    break;
                                }
                                Logger.logDebugMessage(generator.toString());
                                logged = true;
                            }
                        }

                        /**
                         * 循环sortedForgers 中的构造器，找到时间接近于创造区块时间的一个构造器，让其创造一个区块
                         */
                        for (Generator generator : sortedForgers) {
                            Logger.logDebugMessage("++++++++++++++++++++"+generator.getAccountId()+"="+(generator.getHitTime() - generationLimit));
                            /**
                             * 根据时间判断是否生成下一个区块
                             */
                            if (generator.getHitTime() > generationLimit || generator.forge(lastBlock, generationLimit)) {
                                return;
                            }
                        }
                    } finally {
                        BlockchainImpl.getInstance().updateUnlock();
                    }
                } catch (Exception e) {
                    Logger.logMessageWithExcpt("Error in block generation thread", e);
                }
            } catch (Throwable t) {
                Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                t.printStackTrace();
                System.exit(1);
            }

        }

    };

    static {
        if (!Constants.isLightClient) {
            ThreadPool.scheduleThread("GenerateBlocks", generateBlocksThread, 500, TimeUnit.MILLISECONDS);
        }
    }

    public static void init() {}

    public static boolean addListener(Listener<Generator> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<Generator> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    /**
     * 启动锻造，页面点击进入此操作
     * @param secretPhrase 账户密码
     * @return
     */
    public static Generator startForging(String secretPhrase) {
        if (generators.size() >= MAX_FORGERS) {
            throw new RuntimeException("Cannot forge with more than " + MAX_FORGERS + " accounts on the same node");
        }
        /**
         * 根据密码信息生成一个区块构造器
         */
        Generator generator = new Generator(secretPhrase);
        /**
         * 将生成的区块构造器放入generators 中，后续使用
         */
        Generator old = generators.putIfAbsent(secretPhrase, generator);
        if (old != null) {
            Logger.logDebugMessage(old + " is already forging");
            return old;
        }
        listeners.notify(generator, Event.START_FORGING);
        Logger.logDebugMessage(generator + " started");
        return generator;
    }

    public static Generator stopForging(String secretPhrase) {
        Generator generator = generators.remove(secretPhrase);
        if (generator != null) {
            Shareschain.getBlockchain().updateLock();
            try {
                sortedForgers = null;
            } finally {
                Shareschain.getBlockchain().updateUnlock();
            }
            Logger.logDebugMessage(generator + " stopped");
            listeners.notify(generator, Event.STOP_FORGING);
        }
        return generator;
    }

    public static int stopForging() {
        int count = generators.size();
        Iterator<Generator> iter = generators.values().iterator();
        while (iter.hasNext()) {
            Generator generator = iter.next();
            iter.remove();
            Logger.logDebugMessage(generator + " stopped");
            listeners.notify(generator, Event.STOP_FORGING);
        }
        Shareschain.getBlockchain().updateLock();
        try {
            sortedForgers = null;
        } finally {
            Shareschain.getBlockchain().updateUnlock();
        }
        return count;
    }

    public static Generator getGenerator(String secretPhrase) {
        return generators.get(secretPhrase);
    }

    public static int getGeneratorCount() {
        return generators.size();
    }

    public static Collection<Generator> getAllGenerators() {
        return allGenerators;
    }

    public static List<Generator> getSortedForgers() {
        List<Generator> forgers = sortedForgers;
        return forgers == null ? Collections.emptyList() : forgers;
    }

    /**
     * 获取下次的锻造时间
     * @param lastBlockId
     * @param curTime
     * @return
     */
    public static long getNextHitTime(long lastBlockId, int curTime) {
        BlockchainImpl.getInstance().readLock();
        try {
            /**
             * 区块链最后区块的id，与当前锻造者的最后区块id相同，遍历可用的区块锻造器
             * 如果锻造器的下次锻造时间 大于等于（当前时间 - 允许间隔的时间(最大14s)），将直接返回锻造器的下次锻造时间
             * 否则返回0
             * Constants.FORGING_DELAY 延迟生成下一个块的秒数，以便在块中积累更多的交易，但是不能超过14s，
             * 因为最多允许交易的时间戳与块的时间戳有15s的差异，差15s内的交易都可以包含在一个块中
             */
            if (lastBlockId == Generator.lastBlockId && sortedForgers != null) {
                for (Generator generator : sortedForgers) {
                    if (generator.getHitTime() >= curTime - Constants.FORGING_DELAY) {
                        System.out.println("Generator.java in getNextHitTime()----------"+generator.getHitTime());
                        return generator.getHitTime();
                    }
                }
            }
            return 0;
        } finally {
            BlockchainImpl.getInstance().readUnlock();
        }
    }

    public static void setDelay(int delay) {
        Generator.delayTime = delay;
    }

    /**
     * 判断当前的时间戳是否有效
     * 如果距离上次生成区块的时间段为负数，返回false
     * @param hit
     * @param effectiveBalance
     * @param previousBlock
     * @param timestamp
     * @return
     */
    public static boolean verifyHit(BigInteger hit, BigInteger effectiveBalance, Block previousBlock, int timestamp) {
        //距离上次生成区块的时间段
        int elapsedTime = timestamp - previousBlock.getTimestamp();
        if (elapsedTime <= 0) {
            return false;
        }
        //区块baseTarget × 账户余额effectiveBalance = 有效的基准值effectiveBaseTarget
        //有效的基准effectiveBaseTarget × 时间段elapsedTime = 预定的目标值prevTarget
        //预定的目标值prevTarget + 有效的基准值effectiveBaseTarget = 目标值target
        BigInteger effectiveBaseTarget = BigInteger.valueOf(previousBlock.getBaseTarget()).multiply(effectiveBalance);
        BigInteger prevTarget = effectiveBaseTarget.multiply(BigInteger.valueOf(elapsedTime - 1));
        BigInteger target = prevTarget.add(effectiveBaseTarget);
        //需要hit 比 目标值target 小
        //并且hit 比 预定的目标值 prevTarget 大于或等于
        return hit.compareTo(target) < 0
                && (hit.compareTo(prevTarget) >= 0
                || (Constants.isTestnet ? elapsedTime > 300 : elapsedTime > 3600)//距离上个区块生成已过1个小时
                || Constants.isOffline);//ifOffline 单机运行
    }

    public static boolean allowsFakeForging(byte[] publicKey) {
        return Constants.isTestnet && publicKey != null && Arrays.equals(publicKey, fakeForgingPublicKey);
    }

    static BigInteger getHit(byte[] publicKey, Block block) {
        /**
         * 是否允许生成新的区块，在测试环境下使用
         */
        if (allowsFakeForging(publicKey)) {
            return BigInteger.ZERO;
        }

        MessageDigest digest = Crypto.sha256();
        digest.update(block.getGenerationSignature());
        byte[] generationSignatureHash = digest.digest(publicKey);
        return new BigInteger(1, new byte[] {generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0]});
    }

    static long getHitTime(BigInteger effectiveBalance, BigInteger hit, Block block) {
        return block.getTimestamp()
                + hit.divide(BigInteger.valueOf(block.getBaseTarget()).multiply(effectiveBalance)).longValue();
    }


    private final long accountId;
    private final String secretPhrase;
    private final byte[] publicKey;
    private volatile long hitTime;
    private volatile BigInteger hit;
    private volatile BigInteger effectiveBalance;
    private volatile long deadline;

    /**
     *
     * @param secretPhrase 账户密码
     */
    private Generator(String secretPhrase) {
        this.secretPhrase = secretPhrase;
        this.publicKey = Crypto.getPublicKey(secretPhrase);
        this.accountId = Account.getId(publicKey);
        Shareschain.getBlockchain().updateLock();
        try {
            if (Shareschain.getBlockchain().getHeight() >= Constants.LAST_KNOWN_BLOCK) {
                setLastBlock(Shareschain.getBlockchain().getLastBlock());
            }
            sortedForgers = null;
        } finally {
            Shareschain.getBlockchain().updateUnlock();
        }
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getDeadline() {
        return deadline;
    }

    public long getHitTime() {
        return hitTime;
    }

    @Override
    public int compareTo(Generator g) {
        int i = this.hit.multiply(g.effectiveBalance).compareTo(g.hit.multiply(this.effectiveBalance));
        if (i != 0) {
            return i;
        }
        return Long.compare(accountId, g.accountId);
    }

    @Override
    public String toString() {
        return "Forger " + Long.toUnsignedString(accountId) + " deadline " + getDeadline() + " hit " + hitTime;
    }

    private void setLastBlock(Block lastBlock) {
        int height = lastBlock.getHeight();
        /**
         * 获取账户有效余额
         */
        Account account = Account.getAccount(accountId, height);
        if (account == null) {
            effectiveBalance = BigInteger.ZERO;
        } else {
            effectiveBalance = BigInteger.valueOf(Math.max(account.getEffectiveBalanceSCTK(height), 0));
        }
        if (effectiveBalance.signum() == 0) {
            hitTime = 0;
            hit = BigInteger.ZERO;
            return;
        }
        hit = getHit(publicKey, lastBlock);
        hitTime = getHitTime(effectiveBalance, hit, lastBlock);
        deadline = Math.max(hitTime - lastBlock.getTimestamp(), 0);
        Logger.logDebugMessage("hit:"+hit);
        Logger.logDebugMessage("hitTime:"+hitTime);
        Logger.logDebugMessage("effectiveBalance:"+effectiveBalance);
        listeners.notify(this, Event.GENERATION_DEADLINE);
    }

    /**
     * 区块进行生成
     * @param lastBlock
     * @param generationLimit
     * @return
     * @throws BlockchainProcessor.BlockNotAcceptedException
     */
    boolean forge(Block lastBlock, int generationLimit) throws BlockchainProcessor.BlockNotAcceptedException {
        int timestamp = getTimestamp(generationLimit);
        if (!verifyHit(hit, effectiveBalance, lastBlock, timestamp)) {
            Logger.logDebugMessage(this.toString() + " failed to forge at " + timestamp + " height " + lastBlock.getHeight() + " last timestamp " + lastBlock.getTimestamp());
            return false;
        }
        int start = Shareschain.getEpochTime();
        /**
         * 创建区块，直至创建成功
         */
        while (true) {
            try {
                BlockchainProcessorImpl.getInstance().generateBlock(secretPhrase, timestamp);
                setDelay(Constants.FORGING_DELAY);
                return true;
            } catch (BlockchainProcessor.TransactionNotAcceptedException e) {
                // the bad transaction has been expunged, try again
                if (Shareschain.getEpochTime() - start > 10) { // give up after trying for 10 s
                    throw e;
                }
            }
        }
    }

    /**
     * 设置区块时间戳
     * @param generationLimit
     * @return
     */
    private int getTimestamp(int generationLimit) {
        return (generationLimit - hitTime > 3600) ? generationLimit : (int)hitTime + 1;
    }

    /** Active block generators */
    private static final Set<Long> activeGeneratorIds = new HashSet<>();

    /** Active block identifier */
    private static long activeBlockId;

    /** Sorted list of generators for the next block */
    private static final List<ActiveGenerator> activeGenerators = new ArrayList<>();

    /** Generator list has been initialized */
    private static boolean generatorsInitialized = false;

    /**
     * Return a list of generators for the next block.  The caller must hold the blockchain
     * read lock to ensure the integrity of the returned list.
     *
     * @return                      List of generator account identifiers
     */
    public static List<ActiveGenerator> getNextGenerators() {
        List<ActiveGenerator> generatorList;
        Blockchain blockchain = Shareschain.getBlockchain();
        synchronized(activeGenerators) {
            if (!generatorsInitialized) {
                activeGeneratorIds.addAll(BlockDB.getBlockGenerators(Math.max(1, blockchain.getHeight() - 10000)));
                activeGeneratorIds.forEach(activeGeneratorId -> activeGenerators.add(new ActiveGenerator(activeGeneratorId)));
                Logger.logDebugMessage(activeGeneratorIds.size() + " block generators found");
                Shareschain.getBlockchainProcessor().addListener(block -> {
                    long generatorId = block.getGeneratorId();
                    synchronized(activeGenerators) {
                        if (!activeGeneratorIds.contains(generatorId)) {
                            activeGeneratorIds.add(generatorId);
                            activeGenerators.add(new ActiveGenerator(generatorId));
                        }
                    }
                }, BlockchainProcessor.Event.BLOCK_PUSHED);
                generatorsInitialized = true;
            }
            long blockId = blockchain.getLastBlock().getId();
            if (blockId != activeBlockId) {
                activeBlockId = blockId;
                Block lastBlock = blockchain.getLastBlock();
                for (ActiveGenerator generator : activeGenerators) {
                    generator.setLastBlock(lastBlock);
                }
                Collections.sort(activeGenerators);
            }
            generatorList = new ArrayList<>(activeGenerators);
        }
        return generatorList;
    }

    /**
     * Active generator
     */
    public static class ActiveGenerator implements Comparable<ActiveGenerator> {
        private final long accountId;
        private long hitTime;
        private long effectiveBalanceSCTK;
        private byte[] publicKey;

        private ActiveGenerator(long accountId) {
            this.accountId = accountId;
            this.hitTime = Long.MAX_VALUE;
        }

        public long getAccountId() {
            return accountId;
        }

        public long getEffectiveBalance() {
            return effectiveBalanceSCTK;
        }

        public long getHitTime() {
            return hitTime;
        }

        private void setLastBlock(Block lastBlock) {
            if (publicKey == null) {
                publicKey = Account.getPublicKey(accountId);
                if (publicKey == null) {
                    hitTime = Long.MAX_VALUE;
                    return;
                }
            }
            int height = lastBlock.getHeight();
            Account account = Account.getAccount(accountId, height);
            if (account == null) {
                hitTime = Long.MAX_VALUE;
                return;
            }
            effectiveBalanceSCTK = Math.max(account.getEffectiveBalanceSCTK(height), 0);
            if (effectiveBalanceSCTK == 0) {
                hitTime = Long.MAX_VALUE;
                return;
            }
            BigInteger effectiveBalance = BigInteger.valueOf(effectiveBalanceSCTK);
            BigInteger hit = Generator.getHit(publicKey, lastBlock);
            hitTime = Generator.getHitTime(effectiveBalance, hit, lastBlock);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(accountId);
        }

        @Override
        public boolean equals(Object obj) {
            return (obj != null && (obj instanceof ActiveGenerator) && accountId == ((ActiveGenerator)obj).accountId);
        }

        @Override
        public int compareTo(ActiveGenerator obj) {
            return Long.compare(hitTime, obj.hitTime);
        }
    }
}
