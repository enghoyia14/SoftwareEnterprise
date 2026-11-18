package uk.ac.newcastle.enterprisemiddleware.travelagent;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class TravelAgentRestServiceIntegrationTest {
    @Test
    void travelAgentBookingCollectionIsAvailable() {
        given().get("/travelagent/bookings").then().statusCode(200);
    }

    @Test
    void openApiDocumentsAggregateBookingEndpoint() {
        given().get("/q/openapi").then().statusCode(200).body(containsString("/travelagent/bookings"));
    }
}
