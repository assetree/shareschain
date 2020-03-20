
package shareschain.account;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.blockchain.Chain;
import shareschain.blockchain.Mainchain;
import shareschain.database.DBKey;
import shareschain.database.VersionedEntityDBTable;
import shareschain.util.Listener;
import shareschain.util.Listeners;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class BalanceHome {

    public enum Event {
        BALANCE, UNCONFIRMED_BALANCE
    }

    public static BalanceHome forChain(Chain chain) {
        if (chain.getBalanceHome() != null) {
            throw new IllegalStateException("already set");
        }
        return new BalanceHome(chain);
    }

    private static final Listeners<Balance, Event> listeners = new Listeners<>();

    public static boolean addListener(Listener<Balance> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<Balance> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    private final DBKey.LongKeyFactory<Balance> balanceDBKeyFactory;
    private final VersionedEntityDBTable<Balance> balanceTable;
    private final Chain chain;

    private BalanceHome(Chain chain) {
        this.chain = chain;
        this.balanceDBKeyFactory = new DBKey.LongKeyFactory<Balance>("account_id") {
            @Override
            public DBKey newKey(Balance balance) {
                return balance.dbKey == null ? newKey(balance.accountId) : balance.dbKey;
            }
            @Override
            public Balance newEntity(DBKey dbKey) {
                return new Balance(((DBKey.LongKey)dbKey).getId());
            }
        };
        if (chain instanceof Mainchain) {
            this.balanceTable = new VersionedEntityDBTable<Balance>(chain.getSchemaTable("balance_sctk"), balanceDBKeyFactory) {
                @Override
                protected Balance load(Connection con, ResultSet rs, DBKey dbKey) throws SQLException {
                    return new Balance(rs, dbKey);
                }
                @Override
                protected void save(Connection con, Balance balance) throws SQLException {
                    balance.save(con);
                }
                @Override
                public void trim(int height) {
                    if (height <= Constants.GUARANTEED_BALANCE_CONFIRMATIONS) {
                        return;
                    }
                    super.trim(height);
                }
                @Override
                public void checkAvailable(int height) {
                    if (height > Constants.GUARANTEED_BALANCE_CONFIRMATIONS) {
                        super.checkAvailable(height);
                        return;
                    }
                    if (height > Shareschain.getBlockchain().getHeight()) {
                        throw new IllegalArgumentException("Height " + height + " exceeds blockchain height " + Shareschain.getBlockchain().getHeight());
                    }
                }
            };
        } else {
            this.balanceTable = new VersionedEntityDBTable<Balance>(chain.getSchemaTable("balance"), balanceDBKeyFactory) {
                @Override
                protected Balance load(Connection con, ResultSet rs, DBKey dbKey) throws SQLException {
                    return new Balance(rs, dbKey);
                }
                @Override
                protected void save(Connection con, Balance balance) throws SQLException {
                    balance.save(con);
                }
            };
        }
    }

    public Balance getBalance(long accountId) {
        DBKey dbKey = balanceDBKeyFactory.newKey(accountId);
        Balance balance = balanceTable.get(dbKey);
        if (balance == null) {
            balance = balanceTable.newEntity(dbKey);
        }
        return balance;
    }

    public Balance getBalance(long accountId, int height) {
        DBKey dbKey = balanceDBKeyFactory.newKey(accountId);
        Balance balance = balanceTable.get(dbKey, height);
        if (balance == null) {
            balance = new Balance(accountId);
        }
        return balance;
    }

    public final class Balance {

        private final long accountId;
        private final DBKey dbKey;
        private long balance;
        private long unconfirmedBalance;

        Balance(long accountId) {
            this.accountId = accountId;
            this.dbKey = balanceDBKeyFactory.newKey(accountId);
            this.balance = 0L;
            this.unconfirmedBalance = 0L;
        }

        private Balance(ResultSet rs, DBKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.dbKey = dbKey;
            this.balance = rs.getLong("balance");
            this.unconfirmedBalance = rs.getLong("unconfirmed_balance");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO " + balanceTable.getSchemaTable() + " (account_id, "
                    + "balance, unconfirmed_balance, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                pstmt.setLong(++i, this.balance);
                pstmt.setLong(++i, this.unconfirmedBalance);
                pstmt.setInt(++i, Shareschain.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        private void save() {
            if (balance == 0 && unconfirmedBalance == 0) {
                balanceTable.delete(this, true);
            } else {
                balanceTable.insert(this);
            }
        }

        public Chain getChain() {
            return BalanceHome.this.chain;
        }

        public long getAccountId() {
            return accountId;
        }

        public long getBalance() {
            return balance;
        }

        public long getUnconfirmedBalance() {
            return unconfirmedBalance;
        }

        public void addToBalance(AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount) {
            addToBalance(event, eventId, amount, 0);
        }

        public void addToBalance(AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount, long fee) {
            if (amount == 0 && fee == 0) {
                return;
            }
            long totalAmount = Math.addExact(amount, fee);
            this.balance = Math.addExact(this.balance, totalAmount);
            if (chain == Mainchain.mainchain) {
                //更新账户的保证余额
                Account.addToGuaranteedBalanceKER(this.accountId, totalAmount);
            }
            Account.checkBalance(this.accountId, this.balance, this.unconfirmedBalance);
            save();
            listeners.notify(this, Event.BALANCE);
            if (AccountChainLedger.mustLogEntry(event, this.accountId, false)) {
                if (fee != 0) {
                    AccountChainLedger.logEntry(new AccountChainLedger.LedgerEntry(AccountChainLedger.LedgerEvent.TRANSACTION_FEE, eventId, this.accountId,
                            AccountChainLedger.LedgerHolding.COIN_BALANCE, chain.getId(), fee, this.balance - amount));
                }
                if (amount != 0) {
                    AccountChainLedger.logEntry(new AccountChainLedger.LedgerEntry(event, eventId, this.accountId,
                            AccountChainLedger.LedgerHolding.COIN_BALANCE, chain.getId(), amount, this.balance));
                }
            }
        }

        public void addToUnconfirmedBalance(AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount) {
            addToUnconfirmedBalance(event, eventId, amount, 0);
        }

        /**
         *更新余额表
         * @param event
         * @param eventId
         * @param amount
         * @param fee
         */
        void addToUnconfirmedBalance(AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount, long fee) {
            if (amount == 0 && fee == 0) {
                return;
            }
            long totalAmount = Math.addExact(amount, fee);
            //未确认金额 = 未确认金额+ 当前转账金额 + 交易费用
            this.unconfirmedBalance = Math.addExact(this.unconfirmedBalance, totalAmount);
            //检查账户余额是否满足要求
            Account.checkBalance(this.accountId, this.balance, this.unconfirmedBalance);
            //更新余额表
            save();
            listeners.notify(this, Event.UNCONFIRMED_BALANCE);
            //记录账本日志
            if (AccountChainLedger.mustLogEntry(event, this.accountId, true)) {
                if (fee != 0) {
                    AccountChainLedger.logEntry(new AccountChainLedger.LedgerEntry(AccountChainLedger.LedgerEvent.TRANSACTION_FEE, eventId, this.accountId,
                            AccountChainLedger.LedgerHolding.UNCONFIRMED_COIN_BALANCE, chain.getId(), fee, this.unconfirmedBalance - amount));
                }
                if (amount != 0) {
                    AccountChainLedger.logEntry(new AccountChainLedger.LedgerEntry(event, eventId, this.accountId,
                            AccountChainLedger.LedgerHolding.UNCONFIRMED_COIN_BALANCE, chain.getId(), amount, this.unconfirmedBalance));
                }
            }
        }

        public void addToBalanceAndUnconfirmedBalance(AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount) {
            addToBalanceAndUnconfirmedBalance(event, eventId, amount, 0);
        }

        void addToBalanceAndUnconfirmedBalance(AccountChainLedger.LedgerEvent event, AccountChainLedger.LedgerEventId eventId, long amount, long fee) {
            if (amount == 0 && fee == 0) {
                return;
            }
            long totalAmount = Math.addExact(amount, fee);
            this.balance = Math.addExact(this.balance, totalAmount);
            this.unconfirmedBalance = Math.addExact(this.unconfirmedBalance, totalAmount);
            if (chain == Mainchain.mainchain) {
                //更新账户的保证余额表account_guaranteed_balance中的 additions 余额信息
                Account.addToGuaranteedBalanceKER(this.accountId, totalAmount);
            }
            Account.checkBalance(this.accountId, this.balance, this.unconfirmedBalance);
            save();
            listeners.notify(this, Event.BALANCE);
            listeners.notify(this, Event.UNCONFIRMED_BALANCE);
            if (AccountChainLedger.mustLogEntry(event, this.accountId, true)) {
                if (fee != 0) {
                    AccountChainLedger.logEntry(new AccountChainLedger.LedgerEntry(AccountChainLedger.LedgerEvent.TRANSACTION_FEE, eventId, this.accountId,
                            AccountChainLedger.LedgerHolding.UNCONFIRMED_COIN_BALANCE, chain.getId(), fee, this.unconfirmedBalance - amount));
                }
                if (amount != 0) {
                    AccountChainLedger.logEntry(new AccountChainLedger.LedgerEntry(event, eventId, this.accountId,
                            AccountChainLedger.LedgerHolding.UNCONFIRMED_COIN_BALANCE, chain.getId(), amount, this.unconfirmedBalance));
                }
            }
            if (AccountChainLedger.mustLogEntry(event, this.accountId, false)) {
                if (fee != 0) {
                    AccountChainLedger.logEntry(new AccountChainLedger.LedgerEntry(AccountChainLedger.LedgerEvent.TRANSACTION_FEE, eventId, this.accountId,
                            AccountChainLedger.LedgerHolding.COIN_BALANCE, chain.getId(), fee, this.balance - amount));
                }
                if (amount != 0) {
                    AccountChainLedger.logEntry(new AccountChainLedger.LedgerEntry(event, eventId, this.accountId,
                            AccountChainLedger.LedgerHolding.COIN_BALANCE, chain.getId(), amount, this.balance));
                }
            }
        }

    }
}
