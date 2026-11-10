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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
class TaxiAdvancedIntegrationTest {

    @Test
    void guestBookingRollsBackCustomerWhenBookingIsInvalid() {
        long taxiId = createTaxi();
        Map<String, Object> guest = new HashMap<>();
        Map<String, Object> booking = new HashMap<>();
        booking.put("date", LocalDate.now().minusDays(1).toString());
        booking.put("taxi", Map.of("id", taxiId));
        guest.put("customer", customer());
        guest.put("booking", booking);

        given().contentType(ContentType.JSON).body(guest).post("/guest-bookings").then().statusCode(400);
        // Reusing the same unique email succeeding proves the customer insert was rolled back.
        given().contentType(ContentType.JSON).body(guest.get("customer")).post("/customers").then().statusCode(201);
    }

    @Test
    void deletingCustomerOrTaxiCascadesToBookings() {
        long customerId = createCustomer();
        long taxiId = createTaxi();
        createBooking(customerId, taxiId);
        given().delete("/customers/{id}", customerId).then().statusCode(204);
        given().get("/bookings/customer/{id}", customerId).then().statusCode(200).body("size()", is(0));

        long secondCustomer = createCustomer();
        long secondTaxi = createTaxi();
        createBooking(secondCustomer, secondTaxi);
        given().delete("/taxis/{id}", secondTaxi).then().statusCode(204);
        given().get("/bookings/customer/{id}", secondCustomer).then().statusCode(200).body("size()", is(0));
    }

    private static long createCustomer() {
        return ((Number) given().contentType(ContentType.JSON).body(customer()).post("/customers").then()
                .statusCode(201).extract().path("id")).longValue();
    }
    private static long createTaxi() {
        Map<String, Object> taxi = new HashMap<>();
        taxi.put("registration", UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase());
        taxi.put("numberOfSeats", 4);
        return ((Number) given().contentType(ContentType.JSON).body(taxi).post("/taxis").then()
                .statusCode(201).extract().path("id")).longValue();
    }
    private static void createBooking(long customerId, long taxiId) {
        Map<String, Object> booking = new HashMap<>();
        booking.put("customerId", customerId);
        booking.put("taxiId", taxiId);
        booking.put("date", LocalDate.now().plusDays(12).toString());
        given().contentType(ContentType.JSON).body(booking).post("/bookings").then().statusCode(201);
    }
    private static Map<String, Object> customer() {
        Map<String, Object> customer = new HashMap<>();
        customer.put("name", "GuestUser");
        customer.put("email", "guest-" + UUID.randomUUID() + "@example.com");
        customer.put("phoneNumber", "07123456789");
        return customer;
    }
}
