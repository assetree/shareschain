package shareschain;

import shareschain.util.Convert;

import java.math.BigInteger;
import java.text.SimpleDateFormat;

public final class Constants {

    public static final boolean isTestnet = Shareschain.getBooleanProperty("shareschain.isTestnet");
    public static final boolean isOffline = Shareschain.getBooleanProperty("shareschain.isOffline");
    public static final boolean isLightClient = Shareschain.getBooleanProperty("shareschain.isLightClient");
    public static final boolean isPermissioned = Shareschain.getBooleanProperty("shareschain.isPermissioned");
    static {
        if (isPermissioned) {
            try {
                Class.forName("com.jelurida.blockchain.permission.BlockchainRoleMapper");
            } catch (ClassNotFoundException e) {
                throw new ExceptionInInitializerError("BlockchainRoleMapper class required for a permissioned blockchain");
            }
        }
    }
    public static final String ACCOUNT_PREFIX = "";
    public static final int MAX_NUMBER_OF_SMC_TRANSACTIONS = 10;
    public static final int MAX_NUMBER_OF_CHILD_TRANSACTIONS = 100;
    public static final int MAX_CHILDBLOCK_PAYLOAD_LENGTH = 128 * 1024;
    public static final long EPOCH_BEGINNING;
    static {
        try {
            EPOCH_BEGINNING = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z")
                    .parse(isTestnet ? "2017-12-26 14:00:00 +0000" : "2018-01-01 00:00:00 +0000").getTime();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    public static final String customLoginWarning = Shareschain.getStringProperty("shareschain.customLoginWarning", null, false, "UTF-8");

    public static final long MAX_BALANCE_SCTK = 2000000000;
    //1个SCTK等于100000000KER (KER是克拉的意思)
    public static final long KER_PER_SCTK = 100000000;
    public static final long MAX_BALANCE_KER = MAX_BALANCE_SCTK * KER_PER_SCTK;
    public static final int BLOCK_TIME = 60;
    public static final long INITIAL_BASE_TARGET = BigInteger.valueOf(2).pow(63).divide(BigInteger.valueOf(BLOCK_TIME * MAX_BALANCE_SCTK)).longValue(); //153722867;
    public static final long MAX_BASE_TARGET = INITIAL_BASE_TARGET * (isTestnet ? MAX_BALANCE_SCTK : 50);
    public static final long MIN_BASE_TARGET = INITIAL_BASE_TARGET * 9 / 10;
    public static final int MIN_BLOCKTIME_LIMIT = BLOCK_TIME - 7;
    public static final int MAX_BLOCKTIME_LIMIT = BLOCK_TIME + 7;
    public static final int BASE_TARGET_GAMMA = 64;
    public static final int MAX_ROLLBACK = Math.max(Shareschain.getIntProperty("shareschain.maxRollback"), 720);
    public static final int GUARANTEED_BALANCE_CONFIRMATIONS = isTestnet ? Shareschain.getIntProperty("shareschain.testnetGuaranteedBalanceConfirmations", 987654321) : 987654321;
    public static final int LEASING_DELAY = isTestnet ? Shareschain.getIntProperty("shareschain.testnetLeasingDelay", 0) : 0;
    public static final long MIN_FORGING_BALANCE_KER = 1000 * KER_PER_SCTK;

    public static final int MAX_TIMEDRIFT = 15; // allow up to 15 s clock difference
    //延迟生成下一个块的秒数，以便在块中积累更多的交易，但是不能超过14s，因为最多允许交易的时间戳与块的时间戳有15s的差异，差15s内的交易都可以包含在一个块中
    public static final int FORGING_DELAY = Math.min(MAX_TIMEDRIFT - 1, Shareschain.getIntProperty("shareschain.forgingDelay"));
    public static final int FORGING_SPEEDUP = Shareschain.getIntProperty("shareschain.forgingSpeedup");
    public static final int BATCH_COMMIT_SIZE = Shareschain.getIntProperty("shareschain.batchCommitSize", Integer.MAX_VALUE);

    public static final byte MAX_PHASING_VOTE_TRANSACTIONS = 10;

    public static final int MIN_PRUNABLE_LIFETIME = isTestnet ? 1440 * 60 : 14 * 1440 * 60;
    public static final int MAX_PRUNABLE_LIFETIME;
    public static final boolean ENABLE_PRUNING;
    static {
        int maxPrunableLifetime = Shareschain.getIntProperty("shareschain.maxPrunableLifetime");
        ENABLE_PRUNING = maxPrunableLifetime >= 0;
        MAX_PRUNABLE_LIFETIME = ENABLE_PRUNING ? Math.max(maxPrunableLifetime, MIN_PRUNABLE_LIFETIME) : Integer.MAX_VALUE;
    }
    public static final boolean INCLUDE_EXPIRED_PRUNABLE = Shareschain.getBooleanProperty("shareschain.includeExpiredPrunable");

    public static final int MAX_TAGGED_DATA_DATA_LENGTH = 42 * 1024;

    /**
     *暂时修改CHECKSUM_BLOCK_1 值为-1；不对区块链高度进行判断
     */
    public static final int CHECKSUM_BLOCK_1 = Constants.isTestnet ? -1 : -1;

    public static final int LAST_CHECKSUM_BLOCK = CHECKSUM_BLOCK_1;

    public static final int LAST_KNOWN_BLOCK = CHECKSUM_BLOCK_1;

    public static final int[] MIN_VERSION = new int[] {2, 0, 10};
    public static final int[] MIN_PROXY_VERSION = new int[] {2, 0, 10};


    public static final boolean correctInvalidFees = Shareschain.getBooleanProperty("shareschain.correctInvalidFees");

    public static final long LAST_KNOWN_BLOCK_ID = Convert.parseUnsignedLong(isTestnet ? "7136116332013816990" : "5659382559739578917");

    private Constants() {} // never

}
