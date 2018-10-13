
package shareschain.database;

public abstract class PersistentDBTable<T> extends EntityDBTable<T> {

    protected PersistentDBTable(String schemaTable, DBKey.Factory<T> dbKeyFactory) {
        super(schemaTable, dbKeyFactory, false, null);
    }

    protected PersistentDBTable(String schemaTable, DBKey.Factory<T> dbKeyFactory, String fullTextSearchColumns) {
        super(schemaTable, dbKeyFactory, false, fullTextSearchColumns);
    }

    PersistentDBTable(String schemaTable, DBKey.Factory<T> dbKeyFactory, boolean multiversion, String fullTextSearchColumns) {
        super(schemaTable, dbKeyFactory, multiversion, fullTextSearchColumns);
    }

    @Override
    public void rollback(int height) {
    }

    @Override
    public final void truncate() {
    }

    @Override
    public final boolean isPersistent() {
        return true;
    }

}
