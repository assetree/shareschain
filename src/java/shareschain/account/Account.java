
package shareschain.account;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.blockchain.BlockchainProcessor;
import shareschain.blockchain.Chain;
import shareschain.blockchain.Mainchain;
import shareschain.blockchain.Transaction;
import shareschain.util.crypto.Crypto;
import shareschain.util.crypto.EncryptedData;
import shareschain.database.DBClause;
import shareschain.database.DBIterator;
import shareschain.database.DBKey;
import shareschain.database.DBUtils;
import shareschain.database.DerivedDBTable;
import shareschain.database.VersionedEntityDBTable;
import shareschain.database.VersionedPersistentDBTable;
import shareschain.util.Convert;
import shareschain.util.Listener;
import shareschain.util.Listeners;
import shareschain.util.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings({"UnusedDeclaration", "SuspiciousNameCombination"})
public final class Account {

    public enum Event {
        LEASE_SCHEDULED, LEASE_STARTED, LEASE_ENDED, SET_PROPERTY, DELETE_PROPERTY
    }

    public enum ControlType {
        PHASING_ONLY
    }

    public static final class AccountLease {

        private final long lessorId;
        private final DBKey dbKey;
        private long currentLesseeId;
        private int currentLeasingHeightFrom;
        private int currentLeasingHeightTo;
        private long nextLesseeId;
        private int nextLeasingHeightFrom;
        private int nextLeasingHeightTo;

        private AccountLease(long lessorId,
                             int currentLeasingHeightFrom, int currentLeasingHeightTo, long currentLesseeId) {
            this.lessorId = lessorId;
            this.dbKey = accountLeaseDBKeyFactory.newKey(this.lessorId);
            this.currentLeasingHeightFrom = currentLeasingHeightFrom;
            this.currentLeasingHeightTo = currentLeasingHeightTo;
            this.currentLesseeId = currentLesseeId;
        }

        private AccountLease(ResultSet rs, DBKey dbKey) throws SQLException {
            this.lessorId = rs.getLong("lessor_id");
            this.dbKey = dbKey;
            this.currentLeasingHeightFrom = rs.getInt("current_leasing_height_from");
            this.currentLeasingHeightTo = rs.getInt("current_leasing_height_to");
            this.currentLesseeId = rs.getLong("current_lessee_id");
            this.nextLeasingHeightFrom = rs.getInt("next_leasing_height_from");
            this.nextLeasingHeightTo = rs.getInt("next_leasing_height_to");
            this.nextLesseeId = rs.getLong("next_lessee_id");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_lease "
                    + "(lessor_id, current_leasing_height_from, current_leasing_height_to, current_lessee_id, "
                    + "next_leasing_height_from, next_leasing_height_to, next_lessee_id, height, latest) "
                    + "KEY (lessor_id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.lessorId);
                DBUtils.setIntZeroToNull(pstmt, ++i, this.currentLeasingHeightFrom);
                DBUtils.setIntZeroToNull(pstmt, ++i, this.currentLeasingHeightTo);
                DBUtils.setLongZeroToNull(pstmt, ++i, this.currentLesseeId);
                DBUtils.setIntZeroToNull(pstmt, ++i, this.nextLeasingHeightFrom);
                DBUtils.setIntZeroToNull(pstmt, ++i, this.nextLeasingHeightTo);
                DBUtils.setLongZeroToNull(pstmt, ++i, this.nextLesseeId);
                pstmt.setInt(++i, Shareschain.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getLessorId() {
            return lessorId;
        }

        public long getCurrentLesseeId() {
            return currentLesseeId;
        }

        public int getCurrentLeasingHeightFrom() {
            return currentLeasingHeightFrom;
        }

        public int getCurrentLeasingHeightTo() {
            return currentLeasingHeightTo;
        }

        public long getNextLesseeId() {
            return nextLesseeId;
        }

        public int getNextLeasingHeightFrom() {
            return nextLeasingHeightFrom;
        }

        public int getNextLeasingHeightTo() {
            return nextLeasingHeightTo;
        }

    }

    public static final class AccountInfo {

        private final long accountId;
        private final DBKey dbKey;
        private String name;
        private String description;

        private AccountInfo(long accountId, String name, String description) {
            this.accountId = accountId;
            this.dbKey = accountInfoDBKeyFactory.newKey(this.accountId);
            this.name = name;
            this.description = description;
        }

        private AccountInfo(ResultSet rs, DBKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.dbKey = dbKey;
            this.name = rs.getString("name");
            this.description = rs.getString("description");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_info "
                    + "(account_id, name, description, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                DBUtils.setString(pstmt, ++i, this.name);
                DBUtils.setString(pstmt, ++i, this.description);
                pstmt.setInt(++i, Shareschain.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        private void save() {
            if (this.name != null || this.description != null) {
                accountInfoTable.insert(this);
            } else {
                accountInfoTable.delete(this);
            }
        }

    }

    public static final class AccountProperty {

        private final long id;
        private final DBKey dbKey;
        private final long recipientId;
        private final long setterId;
        private String property;
        private String value;

        private AccountProperty(long id, long recipientId, long setterId, String property, String value) {
            this.id = id;
            this.dbKey = accountPropertyDBKeyFactory.newKey(this.id);
            this.recipientId = recipientId;
            this.setterId = setterId;
            this.property = property;
            this.value = value;
        }

        private AccountProperty(ResultSet rs, DBKey dbKey) throws SQLException {
            this.id = rs.getLong("id");
            this.dbKey = dbKey;
            this.recipientId = rs.getLong("recipient_id");
            long setterId = rs.getLong("setter_id");
            this.setterId = setterId == 0 ? recipientId : setterId;
            this.property = rs.getString("property");
            this.value = rs.getString("value");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_property "
                    + "(id, recipient_id, setter_id, property, value, height, latest) "
                    + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setLong(++i, this.recipientId);
                DBUtils.setLongZeroToNull(pstmt, ++i, this.setterId != this.recipientId ? this.setterId : 0);
                DBUtils.setString(pstmt, ++i, this.property);
                DBUtils.setString(pstmt, ++i, this.value);
                pstmt.setInt(++i, Shareschain.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getRecipientId() {
            return recipientId;
        }

        public long getSetterId() {
            return setterId;
        }

        public String getProperty() {
            return property;
        }

        public String getValue() {
            return value;
        }

    }

    public static final class PublicKey {

        private final long accountId;
        private final DBKey dbKey;
        private byte[] publicKey;
        private int height;

        private PublicKey(long accountId, byte[] publicKey) {
            this.accountId = accountId;
            this.dbKey = publicKeyDBKeyFactory.newKey(accountId);
            this.publicKey = publicKey;
            this.height = Shareschain.getBlockchain().getHeight();
        }

        private PublicKey(ResultSet rs, DBKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.dbKey = dbKey;
            this.publicKey = rs.getBytes("public_key");
            this.height = rs.getInt("height");
        }

        private void save(Connection con) throws SQLException {
            height = Shareschain.getBlockchain().getHeight();
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO public_key (account_id, public_key, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, accountId);
                DBUtils.setBytes(pstmt, ++i, publicKey);
                pstmt.setInt(++i, height);
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public int getHeight() {
            return height;
        }

    }

    static class DoubleSpendingException extends RuntimeException {

        DoubleSpendingException(String message, long accountId, long confirmed, long unconfirmed) {
            super(message + " account: " + Long.toUnsignedString(accountId) + " confirmed: " + confirmed + " unconfirmed: " + unconfirmed);
        }

    }

    private static final DBKey.LongKeyFactory<Account> accountDBKeyFactory = new DBKey.LongKeyFactory<Account>("id") {

        @Override
        public DBKey newKey(Account account) {
            return account.dbKey == null ? newKey(account.id) : account.dbKey;
        }

        @Override
        public Account newEntity(DBKey dbKey) {
            return new Account(((DBKey.LongKey)dbKey).getId());
        }

    };

    private static final VersionedEntityDBTable<Account> accountTable = new VersionedEntityDBTable<Account>("public.account", accountDBKeyFactory) {

        @Override
        protected Account load(Connection con, ResultSet rs, DBKey dbKey) throws SQLException {
            return new Account(rs, dbKey);
        }

        @Override
        protected void save(Connection con, Account account) throws SQLException {
            account.save(con);
        }

    };

    private static final DBKey.LongKeyFactory<AccountInfo> accountInfoDBKeyFactory = new DBKey.LongKeyFactory<AccountInfo>("account_id") {

        @Override
        public DBKey newKey(AccountInfo accountInfo) {
            return accountInfo.dbKey;
        }

    };

    private static final DBKey.LongKeyFactory<AccountLease> accountLeaseDBKeyFactory = new DBKey.LongKeyFactory<AccountLease>("lessor_id") {

        @Override
        public DBKey newKey(AccountLease accountLease) {
            return accountLease.dbKey;
        }

    };

    private static final VersionedEntityDBTable<AccountLease> accountLeaseTable = new VersionedEntityDBTable<AccountLease>("public.account_lease",
            accountLeaseDBKeyFactory) {

        @Override
        protected AccountLease load(Connection con, ResultSet rs, DBKey dbKey) throws SQLException {
            return new AccountLease(rs, dbKey);
        }

        @Override
        protected void save(Connection con, AccountLease accountLease) throws SQLException {
            accountLease.save(con);
        }

    };

    private static final VersionedEntityDBTable<AccountInfo> accountInfoTable = new VersionedEntityDBTable<AccountInfo>("public.account_info",
            accountInfoDBKeyFactory, "name,description") {

        @Override
        protected AccountInfo load(Connection con, ResultSet rs, DBKey dbKey) throws SQLException {
            return new AccountInfo(rs, dbKey);
        }

        @Override
        protected void save(Connection con, AccountInfo accountInfo) throws SQLException {
            accountInfo.save(con);
        }

    };

    private static final DBKey.LongKeyFactory<PublicKey> publicKeyDBKeyFactory = new DBKey.LongKeyFactory<PublicKey>("account_id") {

        @Override
        public DBKey newKey(PublicKey publicKey) {
            return publicKey.dbKey;
        }

        @Override
        public PublicKey newEntity(DBKey dbKey) {
            return new PublicKey(((DBKey.LongKey)dbKey).getId(), null);
        }

    };

    private static final VersionedPersistentDBTable<PublicKey> publicKeyTable = new VersionedPersistentDBTable<PublicKey>("public.public_key", publicKeyDBKeyFactory) {

        @Override
        protected PublicKey load(Connection con, ResultSet rs, DBKey dbKey) throws SQLException {
            return new PublicKey(rs, dbKey);
        }

        @Override
        protected void save(Connection con, PublicKey publicKey) throws SQLException {
            publicKey.save(con);
        }

    };

    private static final DerivedDBTable accountGuaranteedBalanceTable = new DerivedDBTable("public.account_guaranteed_balance") {

        @Override
        public void trim(int height) {
            try (Connection con = getConnection();
                 PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM account_guaranteed_balance "
                         + "WHERE height < ? AND height >= 0 LIMIT " + Constants.BATCH_COMMIT_SIZE)) {
                pstmtDelete.setInt(1, height - Constants.GUARANTEED_BALANCE_CONFIRMATIONS);
                int count;
                do {
                    count = pstmtDelete.executeUpdate();
                    db.commitTransaction();
                } while (count >= Constants.BATCH_COMMIT_SIZE);
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

    };

    private static final DBKey.LongKeyFactory<AccountProperty> accountPropertyDBKeyFactory = new DBKey.LongKeyFactory<AccountProperty>("id") {

        @Override
        public DBKey newKey(AccountProperty accountProperty) {
            return accountProperty.dbKey;
        }

    };

    private static final VersionedEntityDBTable<AccountProperty> accountPropertyTable = new VersionedEntityDBTable<AccountProperty>("public.account_property", accountPropertyDBKeyFactory) {

        @Override
        protected AccountProperty load(Connection con, ResultSet rs, DBKey dbKey) throws SQLException {
            return new AccountProperty(rs, dbKey);
        }

        @Override
        protected void save(Connection con, AccountProperty accountProperty) throws SQLException {
            accountProperty.save(con);
        }

    };

    private static final ConcurrentMap<DBKey, byte[]> publicKeyCache = Shareschain.getBooleanProperty("shareschain.enablePublicKeyCache") ?
            new ConcurrentHashMap<>() : null;

    private static final Listeners<AccountLease,Event> leaseListeners = new Listeners<>();

    private static final Listeners<AccountProperty,Event> propertyListeners = new Listeners<>();

    public static boolean addLeaseListener(Listener<AccountLease> listener, Event eventType) {
        return leaseListeners.addListener(listener, eventType);
    }

    public static boolean removeLeaseListener(Listener<AccountLease> listener, Event eventType) {
        return leaseListeners.removeListener(listener, eventType);
    }

    public static boolean addPropertyListener(Listener<AccountProperty> listener, Event eventType) {
        return propertyListeners.addListener(listener, eventType);
    }

    public static boolean removePropertyListener(Listener<AccountProperty> listener, Event eventType) {
        return propertyListeners.removeListener(listener, eventType);
    }

    public static int getCount() {
        return publicKeyTable.getCount();
    }

    public static int getAccountLeaseCount() {
        return accountLeaseTable.getCount();
    }

    public static int getActiveLeaseCount() {
        return accountTable.getCount(new DBClause.NotNullClause("active_lessee_id"));
    }

    public static AccountProperty getProperty(long propertyId) {
        return accountPropertyTable.get(accountPropertyDBKeyFactory.newKey(propertyId));
    }

    public static DBIterator<AccountProperty> getProperties(long recipientId, long setterId, String property, int from, int to) {
        if (recipientId == 0 && setterId == 0) {
            throw new IllegalArgumentException("At least one of recipientId and setterId must be specified");
        }
        DBClause dbClause = null;
        if (setterId == recipientId) {
            dbClause = new DBClause.NullClause("setter_id");
        } else if (setterId != 0) {
            dbClause = new DBClause.LongClause("setter_id", setterId);
        }
        if (recipientId != 0) {
            if (dbClause != null) {
                dbClause = dbClause.and(new DBClause.LongClause("recipient_id", recipientId));
            } else {
                dbClause = new DBClause.LongClause("recipient_id", recipientId);
            }
        }
        if (property != null) {
            dbClause = dbClause.and(new DBClause.StringClause("property", property));
        }
        return accountPropertyTable.getManyBy(dbClause, from, to, " ORDER BY property ");
    }

    public static AccountProperty getProperty(long recipientId, String property) {
        return getProperty(recipientId, property, recipientId);
    }

    public static AccountProperty getProperty(long recipientId, String property, long setterId) {
        if (recipientId == 0 || setterId == 0) {
            throw new IllegalArgumentException("Both recipientId and setterId must be specified");
        }
        DBClause dbClause = new DBClause.LongClause("recipient_id", recipientId);
        dbClause = dbClause.and(new DBClause.StringClause("property", property));
        if (setterId != recipientId) {
            dbClause = dbClause.and(new DBClause.LongClause("setter_id", setterId));
        } else {
            dbClause = dbClause.and(new DBClause.NullClause("setter_id"));
        }
        return accountPropertyTable.getBy(dbClause);
    }

    public static Account getAccount(long id) {
        DBKey dbKey = accountDBKeyFactory.newKey(id);
        Account account = accountTable.get(dbKey);
        if (account == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey != null) {
                account = accountTable.newEntity(dbKey);
                account.publicKey = publicKey;
            }
        }
        return account;
    }

    public static Account getAccount(long id, int height) {
        DBKey dbKey = accountDBKeyFactory.newKey(id);
        Account account = accountTable.get(dbKey, height);
        if (account == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey, height);
            if (publicKey != null) {
                account = new Account(id);
                account.publicKey = publicKey;
            }
        }
        return account;
    }

    public static boolean hasAccount(long id, int height) {
        DBKey dbKey = accountDBKeyFactory.newKey(id);
        if (publicKeyCache != null && Shareschain.getBlockchain().getHeight() == height
                && !Shareschain.getBlockchainProcessor().isScanning() && publicKeyCache.containsKey(dbKey)) {
            return true;
        }
        return publicKeyTable.get(dbKey, height) != null;
    }

    public static Account getAccount(byte[] publicKey) {
        long accountId = getId(publicKey);
        Account account = getAccount(accountId);
        if (account == null) {
            return null;
        }
        if (account.publicKey == null) {
            account.publicKey = publicKeyTable.get(accountDBKeyFactory.newKey(account));
        }
        if (account.publicKey == null || account.publicKey.publicKey == null || Arrays.equals(account.publicKey.publicKey, publicKey)) {
            return account;
        }
        throw new RuntimeException("DUPLICATE KEY for account " + Long.toUnsignedString(accountId)
                + " existing key " + Convert.toHexString(account.publicKey.publicKey) + " new key " + Convert.toHexString(publicKey));
    }

    public static long getId(byte[] publicKey) {
        byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
        return Convert.fullHashToId(publicKeyHash);
    }

    public static byte[] getPublicKey(long id) {
        DBKey dbKey = publicKeyDBKeyFactory.newKey(id);
        byte[] key = null;
        if (publicKeyCache != null) {
            key = publicKeyCache.get(dbKey);
        }
        if (key == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey == null || (key = publicKey.publicKey) == null) {
                return null;
            }
            if (publicKeyCache != null) {
                publicKeyCache.put(dbKey, key);
            }
        }
        return key;
    }

    public static Account addOrGetAccount(long id) {
        if (id == 0) {
            throw new IllegalArgumentException("Invalid accountId 0");
        }
        DBKey dbKey = accountDBKeyFactory.newKey(id);
        Account account = accountTable.get(dbKey);
        if (account == null) {
            account = accountTable.newEntity(dbKey);
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey == null) {
                publicKey = publicKeyTable.newEntity(dbKey);
                publicKeyTable.insert(publicKey);
            }
            account.publicKey = publicKey;
        }
        return account;
    }

    private static DBIterator<AccountLease> getLeaseChangingAccounts(final int height) {
        Connection con = null;
        try {
            con = accountLeaseTable.getConnection();
            PreparedStatement pstmt = con.prepareStatement(
                    "SELECT * FROM account_lease WHERE current_leasing_height_from = ? AND latest = TRUE "
                            + "UNION ALL SELECT * FROM account_lease WHERE current_leasing_height_to = ? AND latest = TRUE "
                            + "ORDER BY current_lessee_id, lessor_id");
            int i = 0;
            pstmt.setInt(++i, height);
            pstmt.setInt(++i, height);
            return accountLeaseTable.getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }


    public static DBIterator<AccountInfo> searchAccounts(String query, int from, int to) {
        return accountInfoTable.search(query, DBClause.EMPTY_CLAUSE, from, to);
    }

    static {

        Shareschain.getBlockchainProcessor().addListener(block -> {
            int height = block.getHeight();
            List<AccountLease> changingLeases = new ArrayList<>();
            try (DBIterator<AccountLease> leases = getLeaseChangingAccounts(height)) {
                while (leases.hasNext()) {
                    changingLeases.add(leases.next());
                }
            }
            for (AccountLease lease : changingLeases) {
                Account lessor = Account.getAccount(lease.lessorId);
                if (height == lease.currentLeasingHeightFrom) {
                    lessor.activeLesseeId = lease.currentLesseeId;
                    leaseListeners.notify(lease, Event.LEASE_STARTED);
                } else if (height == lease.currentLeasingHeightTo) {
                    leaseListeners.notify(lease, Event.LEASE_ENDED);
                    lessor.activeLesseeId = 0;
                    if (lease.nextLeasingHeightFrom == 0) {
                        lease.currentLeasingHeightFrom = 0;
                        lease.currentLeasingHeightTo = 0;
                        lease.currentLesseeId = 0;
                        accountLeaseTable.delete(lease);
                    } else {
                        lease.currentLeasingHeightFrom = lease.nextLeasingHeightFrom;
                        lease.currentLeasingHeightTo = lease.nextLeasingHeightTo;
                        lease.currentLesseeId = lease.nextLesseeId;
                        lease.nextLeasingHeightFrom = 0;
                        lease.nextLeasingHeightTo = 0;
                        lease.nextLesseeId = 0;
                        accountLeaseTable.insert(lease);
                        if (height == lease.currentLeasingHeightFrom) {
                            lessor.activeLesseeId = lease.currentLesseeId;
                            leaseListeners.notify(lease, Event.LEASE_STARTED);
                        }
                    }
                }
                lessor.save();
            }
        }, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);

        if (publicKeyCache != null) {

            Shareschain.getBlockchainProcessor().addListener(block -> {
                publicKeyCache.remove(accountDBKeyFactory.newKey(block.getGeneratorId()));
                block.getSmcTransactions().forEach(smcTransaction -> {
                    publicKeyCache.remove(accountDBKeyFactory.newKey(smcTransaction.getSenderId()));
                });
            }, BlockchainProcessor.Event.BLOCK_POPPED);

            Shareschain.getBlockchainProcessor().addListener(block -> publicKeyCache.clear(), BlockchainProcessor.Event.RESCAN_BEGIN);

        }

    }

    public static void init() {}


    private final long id;
    private final DBKey dbKey;
    private PublicKey publicKey;
    private long forgedBalanceKER;
    private long activeLesseeId;
    private Set<ControlType> controls;

    private Account(long id) {
        if (id != Crypto.rsDecode(Crypto.rsEncode(id))) {
            Logger.logMessageWithExcpt("CRITICAL ERROR: Reed-Solomon encoding fails for " + id);
        }
        this.id = id;
        this.dbKey = accountDBKeyFactory.newKey(this.id);
        this.controls = Collections.emptySet();
    }

    private Account(ResultSet rs, DBKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = dbKey;
        this.forgedBalanceKER = rs.getLong("forged_balance");
        this.activeLesseeId = rs.getLong("active_lessee_id");
        if (rs.getBoolean("has_control_phasing")) {
            controls = Collections.unmodifiableSet(EnumSet.of(ControlType.PHASING_ONLY));
        } else {
            controls = Collections.emptySet();
        }
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account (id, "
                + "forged_balance, active_lessee_id, has_control_phasing, height, latest) "
                + "KEY (id, height) VALUES (?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.forgedBalanceKER);
            DBUtils.setLongZeroToNull(pstmt, ++i, this.activeLesseeId);
            pstmt.setBoolean(++i, controls.contains(ControlType.PHASING_ONLY));
            pstmt.setInt(++i, Shareschain.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    private void save() {
        if (forgedBalanceKER == 0 && activeLesseeId == 0 && controls.isEmpty()) {
            accountTable.delete(this, true);
        } else {
            accountTable.insert(this);
        }
    }

    public long getId() {
        return id;
    }

    public AccountInfo getAccountInfo() {
        return accountInfoTable.get(accountDBKeyFactory.newKey(this));
    }

    public void setAccountInfo(String name, String description) {
        name = Convert.emptyToNull(name.trim());
        description = Convert.emptyToNull(description.trim());
        AccountInfo accountInfo = getAccountInfo();
        if (accountInfo == null) {
            accountInfo = new AccountInfo(id, name, description);
        } else {
            accountInfo.name = name;
            accountInfo.description = description;
        }
        accountInfo.save();
    }

    public AccountLease getAccountLease() {
        return accountLeaseTable.get(accountDBKeyFactory.newKey(this));
    }

    public EncryptedData encryptTo(byte[] data, String senderSecretPhrase, boolean compress) {
        byte[] key = getPublicKey(this.id);
        if (key == null) {
            throw new IllegalArgumentException("Recipient account doesn't have a public key set");
        }
        return Account.encryptTo(key, data, senderSecretPhrase, compress);
    }

    /**
     * 返回加密后的消息和随机数
     * 如果需要压缩,就进行gizp压缩
     * 然后再通过发送者密码、接收人公钥和随机数对压缩后的消息进行加密，并返回
     * @param publicKey 接收人公钥
     * @param data  发送的消息
     * @param senderSecretPhrase 发送人密码
     * @param compress 是否压缩
     * @return
     */
    public static EncryptedData encryptTo(byte[] publicKey, byte[] data, String senderSecretPhrase, boolean compress) {
        if (compress && data.length > 0) {
            data = Convert.compress(data);
        }
        return EncryptedData.encrypt(data, senderSecretPhrase, publicKey);
    }

    public byte[] decryptFrom(EncryptedData encryptedData, String recipientSecretPhrase, boolean uncompress) {
        byte[] key = getPublicKey(this.id);
        if (key == null) {
            throw new IllegalArgumentException("Sender account doesn't have a public key set");
        }
        return Account.decryptFrom(key, encryptedData, recipientSecretPhrase, uncompress);
    }

    public static byte[] decryptFrom(byte[] publicKey, EncryptedData encryptedData, String recipientSecretPhrase, boolean uncompress) {
        byte[] decrypted = encryptedData.decrypt(recipientSecretPhrase, publicKey);
        if (uncompress && decrypted.length > 0) {
            decrypted = Convert.uncompress(decrypted);
        }
        return decrypted;
    }

    public long getForgedBalanceKER() {
        return forgedBalanceKER;
    }

    public long getEffectiveBalanceSCTK() {
        Shareschain.getBlockchain().readLock();
        try {
            return getEffectiveBalanceSCTK(Shareschain.getBlockchain().getHeight());
        } finally {
            Shareschain.getBlockchain().readUnlock();
        }
    }

    /**
     * 获取账户余额
     * @param height
     * @return
     */
    public long getEffectiveBalanceSCTK(int height) {
        /**
         * 如果当前区块高度小于Constants.GUARANTEED_BALANCE_CONFIRMATIONS ，表示区块链中未进行过交易，直接返回账户余额信息
         */
        if (height <= Constants.GUARANTEED_BALANCE_CONFIRMATIONS) {
            BalanceHome.Balance genesisRecipientBalance = Mainchain.mainchain.getBalanceHome().getBalance(id, 0);
            Logger.logDebugMessage("genesisRecipientBalance.getBalance():"+genesisRecipientBalance.getBalance());
            /**
             * Constants.KER_PER_SCTK 1个SCTK等于100000000KER
             */
            return genesisRecipientBalance.getBalance() / Constants.KER_PER_SCTK;
        }
        if (this.publicKey == null) {
            this.publicKey = publicKeyTable.get(accountDBKeyFactory.newKey(this));
        }
        if (this.publicKey == null || this.publicKey.publicKey == null || height - this.publicKey.height <= Constants.GUARANTEED_BALANCE_CONFIRMATIONS) {
            return 0; // cfb: Accounts with the public key revealed less than  blocks ago are not allowed to generate blocks
        }
        long effectiveBalanceKER = getLessorsGuaranteedBalanceKER(height);
        if (activeLesseeId == 0) {
            effectiveBalanceKER += getGuaranteedBalanceKER(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, height);
        }
        return effectiveBalanceKER < Constants.MIN_FORGING_BALANCE_KER ? 0 : effectiveBalanceKER / Constants.KER_PER_SCTK;
    }

    private long getLessorsGuaranteedBalanceKER(int height) {
        List<Account> lessors = new ArrayList<>();
        try (DBIterator<Account> iterator = getLessors(height)) {
            while (iterator.hasNext()) {
                lessors.add(iterator.next());
            }
        }
        if (lessors.isEmpty()) {
            return 0;
        }
        Long[] lessorIds = new Long[lessors.size()];
        long[] balances = new long[lessors.size()];
        for (int i = 0; i < lessors.size(); i++) {
            lessorIds[i] = lessors.get(i).getId();
            balances[i] = Mainchain.mainchain.getBalanceHome().getBalance(lessors.get(i).id, height).getBalance();
        }
        int blockchainHeight = Shareschain.getBlockchain().getHeight();
        try (Connection con = accountGuaranteedBalanceTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT account_id, SUM (additions) AS additions "
                     + "FROM account_guaranteed_balance, TABLE (id BIGINT=?) T WHERE account_id = T.id AND height > ? "
                     + (height < blockchainHeight ? " AND height <= ? " : "")
                     + " GROUP BY account_id ORDER BY account_id")) {
            pstmt.setObject(1, lessorIds);
            pstmt.setInt(2, height - Constants.GUARANTEED_BALANCE_CONFIRMATIONS);
            if (height < blockchainHeight) {
                pstmt.setInt(3, height);
            }
            long total = 0;
            int i = 0;
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long accountId = rs.getLong("account_id");
                    while (lessorIds[i] < accountId && i < lessorIds.length) {
                        total += balances[i++];
                    }
                    if (lessorIds[i] == accountId) {
                        total += Math.max(balances[i++] - rs.getLong("additions"), 0);
                    }
                }
            }
            while (i < balances.length) {
                total += balances[i++];
            }
            return total;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public DBIterator<Account> getLessors() {
        return accountTable.getManyBy(new DBClause.LongClause("active_lessee_id", id), 0, -1, " ORDER BY id ASC ");
    }

    public DBIterator<Account> getLessors(int height) {
        return accountTable.getManyBy(new DBClause.LongClause("active_lessee_id", id), height, 0, -1, " ORDER BY id ASC ");
    }

    public long getGuaranteedBalanceKER() {
        return getGuaranteedBalanceKER(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, Shareschain.getBlockchain().getHeight());
    }

    public long getGuaranteedBalanceKER(final int numberOfConfirmations, final int currentHeight) {
        Shareschain.getBlockchain().readLock();
        try {
            int height = currentHeight - numberOfConfirmations;
            if (height + Constants.GUARANTEED_BALANCE_CONFIRMATIONS < Shareschain.getBlockchainProcessor().getMinRollbackHeight()
                    || height > Shareschain.getBlockchain().getHeight()) {
                throw new IllegalArgumentException("Height " + height + " not available for guaranteed balance calculation");
            }
            long balanceKER = Mainchain.mainchain.getBalanceHome().getBalance(id, currentHeight).getBalance();
            try (Connection con = accountGuaranteedBalanceTable.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT SUM (additions) AS additions "
                         + "FROM account_guaranteed_balance WHERE account_id = ? AND height > ? AND height <= ?")) {
                pstmt.setLong(1, this.id);
                pstmt.setInt(2, height);
                pstmt.setInt(3, currentHeight);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        return balanceKER;
                    }
                    return Math.max(Math.subtractExact(balanceKER, rs.getLong("additions")), 0);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        } finally {
            Shareschain.getBlockchain().readUnlock();
        }
    }


    public Set<ControlType> getControls() {
        return controls;
    }

    public void leaseEffectiveBalance(long lesseeId, int period) {
        int height = Shareschain.getBlockchain().getHeight();
        AccountLease accountLease = accountLeaseTable.get(accountDBKeyFactory.newKey(this));
        if (accountLease == null) {
            accountLease = new AccountLease(id,
                    height + Constants.LEASING_DELAY,
                    height + Constants.LEASING_DELAY + period,
                    lesseeId);
        } else if (accountLease.currentLesseeId == 0) {
            accountLease.currentLeasingHeightFrom = height + Constants.LEASING_DELAY;
            accountLease.currentLeasingHeightTo = height + Constants.LEASING_DELAY + period;
            accountLease.currentLesseeId = lesseeId;
        } else {
            accountLease.nextLeasingHeightFrom = height + Constants.LEASING_DELAY;
            if (accountLease.nextLeasingHeightFrom < accountLease.currentLeasingHeightTo) {
                accountLease.nextLeasingHeightFrom = accountLease.currentLeasingHeightTo;
            }
            accountLease.nextLeasingHeightTo = accountLease.nextLeasingHeightFrom + period;
            accountLease.nextLesseeId = lesseeId;
        }
        accountLeaseTable.insert(accountLease);
        leaseListeners.notify(accountLease, Event.LEASE_SCHEDULED);
    }

    void addControl(ControlType control) {
        if (controls.contains(control)) {
            return;
        }
        EnumSet<ControlType> newControls = EnumSet.of(control);
        newControls.addAll(controls);
        controls = Collections.unmodifiableSet(newControls);
        accountTable.insert(this);
    }

    void removeControl(ControlType control) {
        if (!controls.contains(control)) {
            return;
        }
        EnumSet<ControlType> newControls = EnumSet.copyOf(controls);
        newControls.remove(control);
        controls = Collections.unmodifiableSet(newControls);
        save();
    }

    public void setProperty(Transaction transaction, Account setterAccount, String property, String value) {
        value = Convert.emptyToNull(value);
        AccountProperty accountProperty = getProperty(this.id, property, setterAccount.id);
        if (accountProperty == null) {
            accountProperty = new AccountProperty(transaction.getId(), this.id, setterAccount.id, property, value);
        } else {
            accountProperty.value = value;
        }
        accountPropertyTable.insert(accountProperty);
        propertyListeners.notify(accountProperty, Event.SET_PROPERTY);
    }

    public static void importProperty(long propertyId, long recipientId, long setterId, String property, String value) {
        value = Convert.emptyToNull(value);
        AccountProperty accountProperty = new AccountProperty(propertyId, recipientId, setterId, property, value);
        accountPropertyTable.insert(accountProperty);
    }

    public void deleteProperty(long propertyId) {
        AccountProperty accountProperty = accountPropertyTable.get(accountPropertyDBKeyFactory.newKey(propertyId));
        if (accountProperty == null) {
            return;
        }
        if (accountProperty.getSetterId() != this.id && accountProperty.getRecipientId() != this.id) {
            throw new RuntimeException("Property " + Long.toUnsignedString(propertyId) + " cannot be deleted by " + Long.toUnsignedString(this.id));
        }
        accountPropertyTable.delete(accountProperty);
        propertyListeners.notify(accountProperty, Event.DELETE_PROPERTY);
    }

    public static boolean setOrVerify(long accountId, byte[] key) {
        DBKey dbKey = publicKeyDBKeyFactory.newKey(accountId);
        PublicKey publicKey = publicKeyTable.get(dbKey);
        if (publicKey == null) {
            publicKey = publicKeyTable.newEntity(dbKey);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            publicKey.height = Shareschain.getBlockchain().getHeight();
            return true;
        }
        return Arrays.equals(publicKey.publicKey, key);
    }

    public void apply(byte[] key) {
        PublicKey publicKey = publicKeyTable.get(dbKey);
        if (publicKey == null) {
            publicKey = publicKeyTable.newEntity(dbKey);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            publicKeyTable.insert(publicKey);
        } else if (! Arrays.equals(publicKey.publicKey, key)) {
            throw new IllegalStateException("Public key mismatch");
        } else if (publicKey.height >= Shareschain.getBlockchain().getHeight() - 1) {
            PublicKey dbPublicKey = publicKeyTable.get(dbKey, false);
            if (dbPublicKey == null || dbPublicKey.publicKey == null) {
                publicKeyTable.insert(publicKey);
            }
        }
        if (publicKeyCache != null) {
            publicKeyCache.put(dbKey, key);
        }
        this.publicKey = publicKey;
    }

    public void addToBalance(Chain chain, AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount, long fee) {
        chain.getBalanceHome().getBalance(id).addToBalance(event, eventId, amount, fee);
    }

    public void addToUnconfirmedBalance(Chain chain, AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount, long fee) {
        chain.getBalanceHome().getBalance(id).addToUnconfirmedBalance(event, eventId, amount, fee);
    }

    public void addToBalanceAndUnconfirmedBalance(Chain chain, AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount, long fee) {
        chain.getBalanceHome().getBalance(id).addToBalanceAndUnconfirmedBalance(event, eventId, amount, fee);
    }

    public void addToBalance(Chain chain, AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount) {
        chain.getBalanceHome().getBalance(id).addToBalance(event, eventId, amount);
    }

    public void addToUnconfirmedBalance(Chain chain, AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount) {
        chain.getBalanceHome().getBalance(id).addToUnconfirmedBalance(event, eventId, amount);
    }

    public void addToBalanceAndUnconfirmedBalance(Chain chain, AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount) {
        chain.getBalanceHome().getBalance(id).addToBalanceAndUnconfirmedBalance(event, eventId, amount);
    }

    //添加锻造的费用到余额表
    public void addToForgedBalanceKER(long amount) {
        if (amount == 0) {
            return;
        }
        this.forgedBalanceKER = Math.addExact(this.forgedBalanceKER, amount);
        save();
    }

    static void checkBalance(long accountId, long confirmed, long unconfirmed) {
        if (confirmed < 0) {
            throw new DoubleSpendingException("Negative balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed < 0) {
            throw new DoubleSpendingException("Negative unconfirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed > confirmed) {
            throw new DoubleSpendingException("Unconfirmed exceeds confirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
    }

    /**
     * 更新账户的保证余额
     * @param accountId
     * @param amount
     */
    static void addToGuaranteedBalanceKER(long accountId, long amount) {
        if (amount <= 0) {
            return;
        }
        int blockchainHeight = Shareschain.getBlockchain().getHeight();
        try (Connection con = accountGuaranteedBalanceTable.getConnection();
             PreparedStatement pstmtSelect = con.prepareStatement("SELECT additions FROM account_guaranteed_balance "
                     + "WHERE account_id = ? and height = ?");
             PreparedStatement pstmtUpdate = con.prepareStatement("MERGE INTO account_guaranteed_balance (account_id, "
                     + " additions, height) KEY (account_id, height) VALUES(?, ?, ?)")) {
            pstmtSelect.setLong(1, accountId);
            pstmtSelect.setInt(2, blockchainHeight);
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                long additions = amount;
                if (rs.next()) {
                    additions = Math.addExact(additions, rs.getLong("additions"));
                }
                pstmtUpdate.setLong(1, accountId);
                pstmtUpdate.setLong(2, additions);
                pstmtUpdate.setInt(3, blockchainHeight);
                pstmtUpdate.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public String toString() {
        return "Account " + Long.toUnsignedString(getId());
    }
}
