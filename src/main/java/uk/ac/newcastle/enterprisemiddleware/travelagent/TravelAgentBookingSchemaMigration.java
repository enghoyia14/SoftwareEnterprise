package uk.ac.newcastle.enterprisemiddleware.travelagent;

import io.quarkus.runtime.StartupEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the Taxi-booking-to-aggregate delete cascade to an existing schema.
 * The change only replaces a foreign-key rule; it does not alter or delete rows.
 */
@ApplicationScoped
public class TravelAgentBookingSchemaMigration {
    private static final String AGGREGATE_TABLE = "TRAVEL_AGENT_BOOKING";
    private static final String TAXI_BOOKING_TABLE = "BOOKING";
    private static final String TAXI_BOOKING_COLUMN = "TAXI_BOOKING_ID";
    private static final String ID_COLUMN = "ID";
    private static final String CASCADE_CONSTRAINT_PREFIX = "FK_TA_TAXI_BOOKING_CASCADE";

    @Inject DataSource dataSource;

    void onStart(@Observes StartupEvent ignored) {
        try (Connection connection = dataSource.getConnection()) {
            ensureTaxiBookingCascade(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not migrate the TravelAgent Taxi-booking relationship", e);
        }
    }

    static void ensureTaxiBookingCascade(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        List<ForeignKey> keys = findTaxiBookingKeys(metadata, catalog, schema);
        boolean hasCascade = keys.stream().anyMatch(key -> key.deleteRule == DatabaseMetaData.importedKeyCascade);

        if (!hasCascade) {
            String constraintName = uniqueConstraintName(keys);
            ForeignKey reference = keys.isEmpty()
                    ? new ForeignKey(schema, AGGREGATE_TABLE, TAXI_BOOKING_COLUMN,
                            schema, TAXI_BOOKING_TABLE, ID_COLUMN, constraintName,
                            DatabaseMetaData.importedKeyNoAction)
                    : keys.get(0);
            String table = qualified(metadata, reference.foreignKeySchema, reference.foreignKeyTable);
            String target = qualified(metadata, reference.primaryKeySchema, reference.primaryKeyTable);
            String sql = "ALTER TABLE " + table + " ADD CONSTRAINT " + quote(metadata, constraintName)
                    + " FOREIGN KEY (" + quote(metadata, reference.foreignKeyColumn) + ") REFERENCES "
                    + target + " (" + quote(metadata, reference.primaryKeyColumn) + ") ON DELETE CASCADE";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }

        // A schema may contain both the new cascade key and the old restrictive
        // key after an interrupted upgrade. Remove only the obsolete rule.
        keys = findTaxiBookingKeys(metadata, catalog, schema);
        for (ForeignKey key : keys) {
            if (key.deleteRule != DatabaseMetaData.importedKeyCascade) {
                String sql = "ALTER TABLE " + qualified(metadata, key.foreignKeySchema, key.foreignKeyTable)
                        + " DROP CONSTRAINT " + quote(metadata, key.name);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                }
            }
        }
    }

    private static List<ForeignKey> findTaxiBookingKeys(DatabaseMetaData metadata, String catalog,
                                                        String schema) throws SQLException {
        List<ForeignKey> result = new ArrayList<>();
        try (ResultSet rows = metadata.getImportedKeys(catalog, schema, AGGREGATE_TABLE)) {
            while (rows.next()) {
                if (same(rows.getString("FKTABLE_NAME"), AGGREGATE_TABLE)
                        && same(rows.getString("FKCOLUMN_NAME"), TAXI_BOOKING_COLUMN)
                        && same(rows.getString("PKTABLE_NAME"), TAXI_BOOKING_TABLE)
                        && same(rows.getString("PKCOLUMN_NAME"), ID_COLUMN)) {
                    result.add(new ForeignKey(rows.getString("FKTABLE_SCHEM"), rows.getString("FKTABLE_NAME"),
                            rows.getString("FKCOLUMN_NAME"), rows.getString("PKTABLE_SCHEM"),
                            rows.getString("PKTABLE_NAME"), rows.getString("PKCOLUMN_NAME"),
                            rows.getString("FK_NAME"), rows.getShort("DELETE_RULE")));
                }
            }
        }
        return result;
    }

    private static String uniqueConstraintName(List<ForeignKey> keys) {
        String name = CASCADE_CONSTRAINT_PREFIX;
        int suffix = 1;
        while (containsConstraint(keys, name)) name = CASCADE_CONSTRAINT_PREFIX + "_" + suffix++;
        return name;
    }

    private static boolean containsConstraint(List<ForeignKey> keys, String name) {
        return keys.stream().anyMatch(key -> same(key.name, name));
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String qualified(DatabaseMetaData metadata, String schema, String table) throws SQLException {
        return schema == null || schema.isEmpty()
                ? quote(metadata, table) : quote(metadata, schema) + "." + quote(metadata, table);
    }

    private static String quote(DatabaseMetaData metadata, String identifier) throws SQLException {
        String quote = metadata.getIdentifierQuoteString();
        if (quote == null || quote.trim().isEmpty()) return identifier;
        return quote.trim() + identifier.replace(quote.trim(), quote.trim() + quote.trim()) + quote.trim();
    }

    private static final class ForeignKey {
        final String foreignKeySchema;
        final String foreignKeyTable;
        final String foreignKeyColumn;
        final String primaryKeySchema;
        final String primaryKeyTable;
        final String primaryKeyColumn;
        final String name;
        final int deleteRule;

        ForeignKey(String foreignKeySchema, String foreignKeyTable, String foreignKeyColumn,
                   String primaryKeySchema, String primaryKeyTable, String primaryKeyColumn,
                   String name, int deleteRule) {
            this.foreignKeySchema = foreignKeySchema;
            this.foreignKeyTable = foreignKeyTable;
            this.foreignKeyColumn = foreignKeyColumn;
            this.primaryKeySchema = primaryKeySchema;
            this.primaryKeyTable = primaryKeyTable;
            this.primaryKeyColumn = primaryKeyColumn;
            this.name = name;
            this.deleteRule = deleteRule;
        }
    }
}
