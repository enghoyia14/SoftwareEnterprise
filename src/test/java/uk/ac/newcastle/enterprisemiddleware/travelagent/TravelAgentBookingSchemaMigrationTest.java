package uk.ac.newcastle.enterprisemiddleware.travelagent;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelAgentBookingSchemaMigrationTest {
    @Test
    void upgradesExistingRequiredTaxiForeignKeyWithoutChangingRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:travelagent-migration;DB_CLOSE_DELAY=-1")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE booking (id BIGINT PRIMARY KEY)");
                statement.execute("CREATE TABLE travel_agent_booking (id BIGINT PRIMARY KEY, taxi_booking_id BIGINT NOT NULL)");
                statement.execute("ALTER TABLE travel_agent_booking ADD CONSTRAINT old_taxi_fk "
                        + "FOREIGN KEY (taxi_booking_id) REFERENCES booking(id)");
                statement.execute("INSERT INTO booking(id) VALUES (5)");
                statement.execute("INSERT INTO travel_agent_booking(id, taxi_booking_id) VALUES (1, 5)");
            }

            TravelAgentBookingSchemaMigration.ensureTaxiBookingCascade(connection);

            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM travel_agent_booking")) {
                rows.next();
                assertEquals(1, rows.getInt(1), "the migration preserves the aggregate row");
            }
            boolean cascadeFound = false;
            try (ResultSet keys = connection.getMetaData().getImportedKeys(connection.getCatalog(),
                    connection.getSchema(), "TRAVEL_AGENT_BOOKING")) {
                while (keys.next()) {
                    if ("TAXI_BOOKING_ID".equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))) {
                        assertEquals(DatabaseMetaData.importedKeyCascade, keys.getShort("DELETE_RULE"));
                        cascadeFound = true;
                    }
                }
            }
            assertTrue(cascadeFound, "the Taxi FK must cascade on a Taxi booking delete");

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM booking WHERE id = 5");
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM travel_agent_booking")) {
                rows.next();
                assertEquals(0, rows.getInt(1), "the aggregate is removed with the cancelled Taxi booking");
            }
        }
    }
}
