package uk.ac.newcastle.enterprisemiddleware.travelagent;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Isolated, deterministic service-account IDs for the mocked TravelAgent workflow test. */
public class TravelAgentWorkflowTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.hibernate-orm.sql-load-script", "travelagent-test-data.sql",
                "travelagent.taxi-customer-id", "1",
                "travelagent.hotel-customer-id", "101",
                "travelagent.flight-customer-id", "202"
        );
    }
}
