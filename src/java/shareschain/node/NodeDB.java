
package shareschain.node;

import shareschain.database.Table;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class NodeDB {

    static class Entry {
        private final String address;
        private final long services;
        private final int lastUpdated;

        Entry(String address, long services, int lastUpdated) {
            this.address = address;
            this.services = services;
            this.lastUpdated = lastUpdated;
        }

        public String getAddress() {
            return address;
        }

        public long getServices() {
            return services;
        }

        public int getLastUpdated() {
            return lastUpdated;
        }

        @Override
        public int hashCode() {
            return address.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return (obj != null && (obj instanceof Entry) && address.equals(((Entry)obj).address));
        }
    }

    private static final Table nodeTable = new Table("PUBLIC.NODE");

    static List<Entry> loadNodes() {
        List<Entry> nodes = new ArrayList<>();
        try (Connection con = nodeTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM Node");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                nodes.add(new Entry(rs.getString("address"), rs.getLong("services"), rs.getInt("last_updated")));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return nodes;
    }

    static void deleteNodes(Collection<Entry> nodes) {
        try (Connection con = nodeTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("DELETE FROM node WHERE address = ?")) {
            for (Entry node : nodes) {
                pstmt.setString(1, node.getAddress());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void updateNodes(Collection<Entry> nodes) {
        try (Connection con = nodeTable.getConnection();
                PreparedStatement pstmt = con.prepareStatement("MERGE INTO node "
                        + "(address, services, last_updated) KEY(address) VALUES(?, ?, ?)")) {
            for (Entry node : nodes) {
                pstmt.setString(1, node.getAddress());
                pstmt.setLong(2, node.getServices());
                pstmt.setInt(3, node.getLastUpdated());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void updateNode(NodeImpl node) {
        try (Connection con = nodeTable.getConnection();
                PreparedStatement pstmt = con.prepareStatement("MERGE INTO node "
                        + "(address, services, last_updated) KEY(address) VALUES(?, ?, ?)")) {
            pstmt.setString(1, node.getAnnouncedAddress());
            pstmt.setLong(2, node.getServices());
            pstmt.setInt(3, node.getLastUpdated());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }
}
