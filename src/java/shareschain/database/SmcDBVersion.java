
package shareschain.database;

import shareschain.Constants;
import shareschain.blockchain.BlockDB;
import shareschain.blockchain.BlockchainProcessorImpl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SmcDBVersion extends DBVersion {

    SmcDBVersion(BasicDB db) {
        super(db, "PUBLIC");
    }

    protected void update(int nextUpdate) {
        switch (nextUpdate) {
            case 1:
                apply("CREATE TABLE IF NOT EXISTS block (db_id IDENTITY, id BIGINT NOT NULL, version INT NOT NULL, "
                        + "timestamp INT NOT NULL, previous_block_id BIGINT, total_fee BIGINT NOT NULL, "
                        + "previous_block_hash BINARY(32), cumulative_difficulty VARBINARY NOT NULL, base_target BIGINT NOT NULL, "
                        + "next_block_id BIGINT, "
                        + "height INT NOT NULL, generation_signature BINARY(64) NOT NULL, "
                        + "block_signature BINARY(64) NOT NULL, payload_hash BINARY(32) NOT NULL, generator_id BIGINT NOT NULL)");
            case 2:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS block_id_idx ON block (id)");
            case 3:
                apply("CREATE TABLE IF NOT EXISTS transaction_sctk (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "deadline SMALLINT NOT NULL, recipient_id BIGINT, transaction_index SMALLINT NOT NULL, "
                        + "amount BIGINT NOT NULL, fee BIGINT NOT NULL, full_hash BINARY(32) NOT NULL, "
                        + "height INT NOT NULL, block_id BIGINT NOT NULL, FOREIGN KEY (block_id) REFERENCES block (id) ON DELETE CASCADE, "
                        + "signature BINARY(64) NOT NULL, timestamp INT NOT NULL, type TINYINT NOT NULL, subtype TINYINT NOT NULL, "
                        + "sender_id BIGINT NOT NULL, block_timestamp INT NOT NULL, has_prunable_attachment BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "has_prunable_message BOOLEAN NOT NULL DEFAULT FALSE, has_prunable_encrypted_message BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "ec_block_height INT DEFAULT NULL, ec_block_id BIGINT DEFAULT NULL, attachment_bytes VARBINARY, version TINYINT NOT NULL)");
            case 4:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS transaction_sctk_id_idx ON transaction_sctk (id)");
            case 5:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS block_height_idx ON block (height)");
            case 6:
                apply("CREATE INDEX IF NOT EXISTS block_generator_id_idx ON block (generator_id)");
            case 7:
                apply("CREATE INDEX IF NOT EXISTS transaction_sctk_sender_id_idx ON transaction_sctk (sender_id)");
            case 8:
                apply("CREATE INDEX IF NOT EXISTS transaction_sctk_recipient_id_idx ON transaction_sctk (recipient_id)");
            case 9:
                apply("CREATE TABLE IF NOT EXISTS node (address VARCHAR PRIMARY KEY, last_updated INT, services BIGINT)");
            case 10:
                apply("CREATE INDEX IF NOT EXISTS transaction_sctk_block_timestamp_idx ON transaction_sctk (block_timestamp DESC)");
            case 11:
                apply("CREATE TABLE IF NOT EXISTS account (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "has_control_phasing BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "forged_balance BIGINT NOT NULL, active_lessee_id BIGINT, height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 12:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_id_height_idx ON account (id, height DESC)");
            case 15:
                apply("CREATE TABLE IF NOT EXISTS account_guaranteed_balance (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "additions BIGINT NOT NULL, height INT NOT NULL)");
            case 16:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_guaranteed_balance_id_height_idx ON account_guaranteed_balance "
                        + "(account_id, height DESC)");
            case 17:
                apply("CREATE TABLE IF NOT EXISTS unconfirmed_transaction (db_id IDENTITY, id BIGINT NOT NULL, expiration INT NOT NULL, "
                        + "transaction_height INT NOT NULL, fee BIGINT NOT NULL, fee_per_byte BIGINT NOT NULL, arrival_timestamp BIGINT NOT NULL, "
                        + "is_bundled BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "transaction_bytes VARBINARY NOT NULL, chain_id INT NOT NULL, height INT NOT NULL)");
            case 18:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS unconfirmed_transaction_id_idx ON unconfirmed_transaction (id)");
            case 19:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS block_timestamp_idx ON block (timestamp DESC)");
            case 20:
                apply("CREATE INDEX IF NOT EXISTS unconfirmed_transaction_chain_id_idx ON unconfirmed_transaction (chain_id)");
            case 21:
                apply("CREATE TABLE IF NOT EXISTS scan (rescan BOOLEAN NOT NULL DEFAULT FALSE, height INT NOT NULL DEFAULT 0, "
                        + "validate BOOLEAN NOT NULL DEFAULT FALSE)");
            case 22:
                apply("INSERT INTO scan (rescan, height, validate) VALUES (false, 0, false)");
            case 23:
                apply("CREATE TABLE IF NOT EXISTS public_key (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "public_key BINARY(32), height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 24:
                apply("CREATE INDEX IF NOT EXISTS account_guaranteed_balance_height_idx ON account_guaranteed_balance(height)");
            case 25:
                apply("CREATE TABLE IF NOT EXISTS account_info (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "name VARCHAR, description VARCHAR, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 26:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_info_id_height_idx ON account_info (account_id, height DESC)");
            case 27:
                apply("CREATE INDEX IF NOT EXISTS account_active_lessee_id_idx ON account (active_lessee_id)");
            case 28://账户租赁表
                apply("CREATE TABLE IF NOT EXISTS account_lease (db_id IDENTITY, lessor_id BIGINT NOT NULL, "
                        + "current_leasing_height_from INT, current_leasing_height_to INT, current_lessee_id BIGINT, "
                        + "next_leasing_height_from INT, next_leasing_height_to INT, next_lessee_id BIGINT, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 29:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_lease_lessor_id_height_idx ON account_lease (lessor_id, height DESC)");
            case 30:
                apply("CREATE INDEX IF NOT EXISTS account_lease_current_leasing_height_from_idx ON account_lease (current_leasing_height_from)");
            case 31:
                apply("CREATE INDEX IF NOT EXISTS account_lease_current_leasing_height_to_idx ON account_lease (current_leasing_height_to)");
            case 32:
                apply("CREATE INDEX IF NOT EXISTS account_lease_height_id_idx ON account_lease (height, lessor_id)");
            case 33:
                apply("CREATE INDEX IF NOT EXISTS unconfirmed_transaction_expiration_idx ON unconfirmed_transaction (expiration DESC)");
            case 34:
                apply("CREATE INDEX IF NOT EXISTS account_height_id_idx ON account (height, id)");
            case 35:
                apply("CREATE INDEX IF NOT EXISTS account_info_height_id_idx ON account_info (height, account_id)");
            case 36:
                apply("CREATE TABLE IF NOT EXISTS account_ledger (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "event_type TINYINT NOT NULL, event_id BIGINT NOT NULL, event_hash BINARY(32), chain_id INT NOT NULL, "
                        + "holding_type TINYINT NOT NULL, holding_id BIGINT, change BIGINT NOT NULL, balance BIGINT NOT NULL, "
                        + "block_id BIGINT NOT NULL, height INT NOT NULL, timestamp INT NOT NULL)");
            case 37:
                apply("CREATE INDEX IF NOT EXISTS account_ledger_id_idx ON account_ledger(account_id, db_id)");
            case 38:
                apply("CREATE INDEX IF NOT EXISTS account_ledger_height_idx ON account_ledger(height)");
            case 39:
                FullTextTrigger.init(db);
                apply(null);
            case 40:
                apply("CREATE TABLE IF NOT EXISTS account_control_phasing (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "whitelist ARRAY, voting_model TINYINT NOT NULL, quorum BIGINT, expression VARCHAR, min_balance BIGINT, "
                        + "holding_id BIGINT, min_balance_model TINYINT, max_fees_chains ARRAY, max_fees ARRAY, min_duration SMALLINT, max_duration SMALLINT, "
                        + "sender_property_setter_id BIGINT, sender_property_name VARCHAR, sender_property_value VARCHAR, recipient_property_setter_id BIGINT, "
                        + "recipient_property_name VARCHAR, recipient_property_value VARCHAR, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 41:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_control_phasing_id_height_idx ON account_control_phasing (account_id, height DESC)");
            case 42:
                apply("CREATE INDEX IF NOT EXISTS account_control_phasing_height_id_idx ON account_control_phasing (height, account_id)");
            case 43:
                apply("CREATE TABLE IF NOT EXISTS account_property (db_id IDENTITY, id BIGINT NOT NULL, recipient_id BIGINT NOT NULL, setter_id BIGINT, "
                        + "property VARCHAR NOT NULL, value VARCHAR, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 44:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_property_id_height_idx ON account_property (id, height DESC)");
            case 45:
                apply("CREATE INDEX IF NOT EXISTS account_property_height_id_idx ON account_property (height, id)");
            case 46:
                apply("CREATE INDEX IF NOT EXISTS account_property_recipient_height_idx ON account_property (recipient_id, height DESC)");
            case 47:
                apply("CREATE INDEX IF NOT EXISTS account_property_setter_recipient_idx ON account_property (setter_id, recipient_id)");
            case 48:
                apply("CREATE TABLE IF NOT EXISTS balance_sctk (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "balance BIGINT NOT NULL, unconfirmed_balance BIGINT NOT NULL, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 49:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS balance_sctk_id_height_idx ON balance_sctk (account_id, height DESC)");
            case 50:
                apply("CREATE INDEX IF NOT EXISTS balance_sctk_height_id_idx ON balance_sctk (height, account_id)");
            case 51:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS public_key_account_id_height_idx ON public_key (account_id, height DESC)");
            case 52:
                apply("CREATE TABLE IF NOT EXISTS prunable_message (db_id IDENTITY, id BIGINT NOT NULL, full_hash BINARY(32) NOT NULL, sender_id BIGINT NOT NULL, "
                        + "recipient_id BIGINT, message VARBINARY, message_is_text BOOLEAN NOT NULL, is_compressed BOOLEAN NOT NULL, "
                        + "encrypted_message VARBINARY, encrypted_is_text BOOLEAN DEFAULT FALSE, "
                        + "block_timestamp INT NOT NULL, transaction_timestamp INT NOT NULL, height INT NOT NULL)");
            case 53:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_id_idx ON prunable_message (id)");
            case 54:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_transaction_timestamp_idx ON prunable_message (transaction_timestamp DESC)");
            case 55:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_sender_idx ON prunable_message (sender_id)");
            case 56:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_recipient_idx ON prunable_message (recipient_id)");
            case 57:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_block_timestamp_dbid_idx ON prunable_message (block_timestamp DESC, db_id DESC)");
            case 58:
                apply("CREATE TABLE IF NOT EXISTS account_control_phasing_sub_poll (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "name VARCHAR, whitelist ARRAY, voting_model TINYINT NOT NULL, quorum BIGINT, min_balance BIGINT, "
                        + "sender_property_setter_id BIGINT, sender_property_name VARCHAR, sender_property_value VARCHAR, "
                        + "recipient_property_setter_id BIGINT, recipient_property_name VARCHAR, recipient_property_value VARCHAR, "
                        + "holding_id BIGINT, min_balance_model TINYINT, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 59:
                apply("CREATE INDEX IF NOT EXISTS account_control_phasing_sub_poll_id_height_idx ON account_control_phasing_sub_poll (account_id, height DESC)");
            case 60:
                apply("CREATE INDEX IF NOT EXISTS account_control_phasing_sub_poll_height_id_idx ON account_control_phasing_sub_poll (height, account_id)");
            case 61:
                apply(null);
            case 62:
                FullTextTrigger.migrateToV7(db);
                apply(null);
            case 63:
                apply(null);
            case 64:
                try (Connection con = db.getConnection(schema);
                     Statement stmt = con.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT CONSTRAINT_NAME, TABLE_NAME FROM INFORMATION_SCHEMA.CONSTRAINTS "
                             + "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME IN ('PUBLIC_KEY', 'PRUNABLE_MESSAGE') AND COLUMN_LIST='HEIGHT'")) {
                    List<String> tables = new ArrayList<>();
                    List<String> constraints = new ArrayList<>();
                    while (rs.next()) {
                        tables.add(rs.getString("TABLE_NAME"));
                        constraints.add(rs.getString("CONSTRAINT_NAME"));
                    }
                    for (int i = 0; i < tables.size(); i++) {
                        stmt.executeUpdate("ALTER TABLE " + tables.get(i) + " DROP CONSTRAINT " + constraints.get(i));
                    }
                    apply(null);
                } catch (SQLException e) {
                    throw new RuntimeException(e.toString(), e);
                }
            case 65:
                apply("CREATE INDEX IF NOT EXISTS public_key_height_idx ON public_key (height)");
            case 66:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_height_idx ON prunable_message (height)");
            case 67:
                if (BlockDB.getBlockCount() > 0) {
                    BlockchainProcessorImpl.getInstance().scheduleScan(Constants.CHECKSUM_BLOCK_1 - 1, true);
                }

                apply(null);
            case 68:
                return;
            default:
                throw new RuntimeException("Forging chain database inconsistent with code, at update " + nextUpdate
                        + ", probably trying to run older code on newer database");
        }
    }
}
