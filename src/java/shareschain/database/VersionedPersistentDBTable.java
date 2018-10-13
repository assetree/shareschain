
package shareschain.database;

public abstract class VersionedPersistentDBTable<T> extends PersistentDBTable<T> {

    protected VersionedPersistentDBTable(String schemaTable, DBKey.Factory<T> dbKeyFactory) {
        super(schemaTable, dbKeyFactory, true, null);
    }
}
