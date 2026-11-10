package uk.ac.newcastle.enterprisemiddleware.taxi;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class TaxiRestServiceIntegrationTest {

    @Test
    void customerCanBeCreatedAndListedAndInvalidCustomerIsRejected() {
        Map<String, Object> customer = customer();
        String email = (String) customer.get("email");
        given().contentType(ContentType.JSON).body(customer).when().post("/customers")
                .then().statusCode(201).body("id", notNullValue()).body("name", equalTo(customer.get("name")));
        given().when().get("/customers").then().statusCode(200).body("find { it.email == '%s' }.name".formatted(email),
                equalTo(customer.get("name")));
        customer.put("phoneNumber", "123");
        given().contentType(ContentType.JSON).body(customer).when().post("/customers").then().statusCode(400);
    }

    @Test
    void duplicateCustomerEmailIsRejected() {
        Map<String, Object> customer = customer();
        given().contentType(ContentType.JSON).body(customer).post("/customers").then().statusCode(201);
        given().contentType(ContentType.JSON).body(customer).post("/customers").then().statusCode(409);
    }

    @Test
    void taxiCanBeCreatedAndListedAndInvalidOrDuplicateTaxiIsRejected() {
        Map<String, Object> taxi = taxi();
        String registration = (String) taxi.get("registration");
        given().contentType(ContentType.JSON).body(taxi).post("/taxis").then()
                .statusCode(201).body("id", notNullValue()).body("registration", equalTo(registration));
        given().get("/taxis").then().statusCode(200)
                .body("find { it.registration == '%s' }.numberOfSeats".formatted(registration), equalTo(4));
        given().contentType(ContentType.JSON).body(taxi).post("/taxis").then().statusCode(409);
        taxi.put("numberOfSeats", 21);
        taxi.put("registration", nextRegistration());
        given().contentType(ContentType.JSON).body(taxi).post("/taxis").then().statusCode(400);
    }

    @Test
    void bookingCanBeCreatedListedCancelledAndRemade() {
        long customerId = createCustomer();
        long taxiId = createTaxi();
        LocalDate date = LocalDate.now().plusDays(10);
        Map<String, Object> request = bookingRequest(customerId, taxiId, date);
        long bookingId = ((Number) given().contentType(ContentType.JSON).body(request).post("/bookings").then()
                .statusCode(201).body("customer.id", equalTo((int) customerId))
                .body("taxi.id", equalTo((int) taxiId)).extract().path("id")).longValue();
        given().get("/bookings/customer/{id}", customerId).then().statusCode(200)
                .body("size()", is(1)).body("[0].id", equalTo((int) bookingId));
        given().contentType(ContentType.JSON).body(request).post("/bookings").then().statusCode(409);
        given().delete("/bookings/{id}", bookingId).then().statusCode(204);
        given().delete("/bookings/{id}", bookingId).then().statusCode(404);
        given().contentType(ContentType.JSON).body(request).post("/bookings").then().statusCode(201);
    }

    @Test
    void invalidBookingDatesAndUnknownReferencesAreRejected() {
        long customerId = createCustomer();
        long taxiId = createTaxi();
        given().contentType(ContentType.JSON).body(bookingRequest(customerId, taxiId, LocalDate.now().minusDays(1)))
                .post("/bookings").then().statusCode(400);
        given().contentType(ContentType.JSON).body(bookingRequest(customerId, taxiId + 9999, LocalDate.now().plusDays(5)))
                .post("/bookings").then().statusCode(404);
    }

    private static long createCustomer() {
        return ((Number) given().contentType(ContentType.JSON).body(customer()).post("/customers").then()
                .statusCode(201).extract().path("id")).longValue();
    }

    private static long createTaxi() {
        return ((Number) given().contentType(ContentType.JSON).body(taxi()).post("/taxis").then()
                .statusCode(201).extract().path("id")).longValue();
    }

    private static Map<String, Object> customer() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "TaxiUser");
        body.put("email", "taxi-" + UUID.randomUUID() + "@example.com");
        body.put("phoneNumber", "07123456789");
        return body;
    }

    private static Map<String, Object> taxi() {
        Map<String, Object> body = new HashMap<>();
        body.put("registration", nextRegistration());
        body.put("numberOfSeats", 4);
        return body;
    }

    private static String nextRegistration() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
    }

    private static Map<String, Object> bookingRequest(long customerId, long taxiId, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("taxiId", taxiId);
        body.put("date", date.toString());
        return body;
    }
}
