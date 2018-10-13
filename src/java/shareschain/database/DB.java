
package shareschain.database;

import shareschain.Constants;
import shareschain.Shareschain;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class DB {

    public static final String PREFIX = Constants.isTestnet ? "shareschain.testDB" : "shareschain.db";
    public static final TransactionalDB db = new TransactionalDB(new BasicDB.DBProperties()
            .maxCacheSize(Shareschain.getIntProperty("shareschain.dbCacheKB"))
            .dbUrl(Shareschain.getStringProperty(PREFIX + "Url"))
            .dbType(Shareschain.getStringProperty(PREFIX + "Type"))
            .dbDir(Shareschain.getStringProperty(PREFIX + "Dir"))
            .dbParams(Shareschain.getStringProperty(PREFIX + "Params"))
            .dbUsername(Shareschain.getStringProperty(PREFIX + "Username"))
            .dbPassword(Shareschain.getStringProperty(PREFIX + "Password", null, true))
            .maxConnections(Shareschain.getIntProperty("shareschain.maxDBConnections"))
            .loginTimeout(Shareschain.getIntProperty("shareschain.dbLoginTimeout"))
            .defaultLockTimeout(Shareschain.getIntProperty("shareschain.dbDefaultLockTimeout") * 1000)
            .maxMemoryRows(Shareschain.getIntProperty("shareschain.dbMaxMemoryRows"))
    );

    public static Connection getConnection() throws SQLException {
        return db.getConnection("PUBLIC");
    }

    public static void init() {
        Init.init();
    }

    private static class Init {
        private static void init() {}
        static {
            List<DBVersion> dbVersions = new ArrayList<>();
            dbVersions.add(new SmcDBVersion(db));
            db.init(dbVersions);
        }
    }

    public static void shutdown() {
        db.shutdown();
    }

    private DB() {} // never

}
