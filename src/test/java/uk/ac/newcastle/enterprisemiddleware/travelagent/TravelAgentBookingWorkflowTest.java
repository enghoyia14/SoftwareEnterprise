package uk.ac.newcastle.enterprisemiddleware.travelagent;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.WebApplicationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestProfile(TravelAgentWorkflowTestProfile.class)
class TravelAgentBookingWorkflowTest {
    private static final long HOTEL_BOOKING_ID = 71001L;
    private static final long FLIGHT_BOOKING_ID = 82001L;

    private List<String> calls;
    private long customerId;
    private long taxiId;
    private long lastTaxiBookingId;
    private boolean hotelFails;
    private boolean flightFails;
    private boolean flightNotFound;
    private boolean flightCancelReturnsNotFound;
    private boolean flightCancelFails;
    private boolean hotelCancelFails;
    private boolean flightCancelled;
    private boolean hotelCancelled;
    private boolean hotelCustomerNotFound;
    private boolean hotelNotFound;
    private int hotelCustomerListMissingResponsesRemaining;
    private int hotelListMissingResponsesRemaining;
    private int flightCustomerLookupFailuresRemaining;
    private int hotelBookingNotFoundResponsesRemaining;

    @BeforeEach
    void setUp() {
        calls = new ArrayList<>();
        hotelFails = false;
        flightFails = false;
        flightNotFound = false;
        flightCancelReturnsNotFound = false;
        flightCancelFails = false;
        hotelCancelFails = false;
        flightCancelled = false;
        hotelCancelled = false;
        hotelCustomerNotFound = false;
        hotelNotFound = false;
        hotelCustomerListMissingResponsesRemaining = 0;
        hotelListMissingResponsesRemaining = 0;
        flightCustomerLookupFailuresRemaining = 0;
        hotelBookingNotFoundResponsesRemaining = 0;

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Map<String, Object> customer = new HashMap<>();
        customer.put("name", "TravelAgent");
        customer.put("email", "travelagent-" + suffix + "@example.test");
        customer.put("phoneNumber", "01234567890");
        customerId = given().contentType(MediaType.APPLICATION_JSON).body(customer)
                .post("/customers").then().statusCode(201).extract().jsonPath().getLong("id");

        Map<String, Object> taxi = new HashMap<>();
        taxi.put("registration", "TA" + suffix.substring(0, 5).toUpperCase());
        taxi.put("numberOfSeats", 4);
        taxiId = given().contentType(MediaType.APPLICATION_JSON).body(taxi)
                .post("/taxis").then().statusCode(201).extract().jsonPath().getLong("id");
        lastTaxiBookingId = -1L;

        installStubClients();
    }

    @Test
    void createsListsAndCancelsAllPartsOfAnAggregateBooking() {
        String date = LocalDate.now().plusDays(30).toString();
        Map<String, Object> request = bookingRequest(date);

        String created = given().contentType(MediaType.APPLICATION_JSON).body(request)
                .post("/travelagent/bookings").then().statusCode(201).extract().asString();
        long aggregateId = io.restassured.path.json.JsonPath.from(created).getLong("id");
        long taxiBookingId = io.restassured.path.json.JsonPath.from(created).getLong("taxiBookingId");
        assertEquals(HOTEL_BOOKING_ID,
                io.restassured.path.json.JsonPath.from(created).getLong("hotelBookingId"));
        assertEquals(FLIGHT_BOOKING_ID,
                io.restassured.path.json.JsonPath.from(created).getLong("flightBookingId"));

        String retrieved = given().get("/travelagent/bookings/{id}", aggregateId)
                .then().statusCode(200).extract().asString();
        assertEquals(HOTEL_BOOKING_ID,
                io.restassured.path.json.JsonPath.from(retrieved).getLong("hotelBookingId"));
        assertEquals(FLIGHT_BOOKING_ID,
                io.restassured.path.json.JsonPath.from(retrieved).getLong("flightBookingId"));

        given().queryParam("customerId", customerId).get("/travelagent/bookings")
                .then().statusCode(200).body("size()", org.hamcrest.Matchers.equalTo(1));

        given().delete("/travelagent/bookings/{id}", aggregateId).then().statusCode(204);
        assertTaxiBookingAbsent(taxiBookingId);
        given().get("/travelagent/bookings/{id}", aggregateId).then().statusCode(404);
        assertEquals(List.of("taxi:create", "hotel:create", "flight:create",
                "flight:cancel", "hotel:cancel", "taxi:cancel"), calls);
    }

    @Test
    void compensatesTaxiAndHotelWhenFlightClientThrowsConflict() {
        flightFails = true;
        Map<String, Object> request = bookingRequest(LocalDate.now().plusDays(31).toString());

        given().contentType(MediaType.APPLICATION_JSON).body(request)
                .post("/travelagent/bookings").then().statusCode(409);

        given().queryParam("customerId", customerId).get("/travelagent/bookings")
                .then().statusCode(200).body("size()", org.hamcrest.Matchers.equalTo(0));
        assertTaxiBookingAbsent(lastTaxiBookingId);
        assertEquals(List.of("taxi:create", "hotel:create", "flight:create",
                "hotel:cancel", "taxi:cancel"), calls);
    }

    @Test
    void compensatesTaxiWhenHotelBookingIsRejected() {
        hotelFails = true;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().plusDays(31).toString()))
                .post("/travelagent/bookings")
                .then().statusCode(409)
                .body("error", org.hamcrest.Matchers.containsString("Hotel service returned HTTP 409"));

        given().queryParam("customerId", customerId).get("/travelagent/bookings")
                .then().statusCode(200).body("size()", org.hamcrest.Matchers.equalTo(0));
        assertTaxiBookingAbsent(lastTaxiBookingId);
        assertEquals(List.of("taxi:create", "hotel:create", "taxi:cancel"), calls);
    }

    @Test
    void rejectsPastTravelAgentBookingDateBeforeCallingServices() {
        given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().minusDays(1).toString()))
                .post("/travelagent/bookings")
                .then().statusCode(400);

        assertTrue(calls.isEmpty(), "Invalid aggregate requests must be rejected before any service call");
    }

    @Test
    void identifiesFlightWhenItsBookingEndpointReturnsNotFound() {
        flightNotFound = true;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().plusDays(33).toString()))
                .post("/travelagent/bookings")
                .then().statusCode(400)
                .body("error", org.hamcrest.Matchers.containsString("Flight service returned HTTP 404"));

        assertEquals(List.of("taxi:create", "hotel:create", "flight:create", "flight:create", "flight:create",
                "hotel:cancel", "taxi:cancel"), calls);
    }

    @Test
    void retriesTransientFlightCustomerNotFoundBeforeCreatingBookings() {
        flightCustomerLookupFailuresRemaining = 1;
        String created = given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().plusDays(37).toString()))
                .post("/travelagent/bookings").then().statusCode(201).extract().asString();
        long aggregateId = io.restassured.path.json.JsonPath.from(created).getLong("id");

        given().delete("/travelagent/bookings/{id}", aggregateId).then().statusCode(204);
        assertEquals(List.of("taxi:create", "hotel:create", "flight:create",
                "flight:cancel", "hotel:cancel", "taxi:cancel"), calls);
    }

    @Test
    void retriesTransientHotelBookingNotFoundBeforeCompensating() {
        hotelBookingNotFoundResponsesRemaining = 1;
        String created = given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().plusDays(38).toString()))
                .post("/travelagent/bookings").then().statusCode(201).extract().asString();
        long aggregateId = io.restassured.path.json.JsonPath.from(created).getLong("id");

        given().delete("/travelagent/bookings/{id}", aggregateId).then().statusCode(204);
        assertEquals(List.of("taxi:create", "hotel:create", "hotel:create", "flight:create",
                "flight:cancel", "hotel:cancel", "taxi:cancel"), calls);
    }

    @Test
    void reportsMissingHotelCustomerBeforeCreatingTaxiBooking() {
        hotelCustomerNotFound = true;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().plusDays(35).toString()))
                .post("/travelagent/bookings")
                .then().statusCode(400)
                .body("error", org.hamcrest.Matchers.containsString("Hotel customer ID 101 was not present in GET /customers"));

        assertTrue(calls.isEmpty(), "No component booking should be created when the Hotel account is missing");
    }

    @Test
    void reportsMissingHotelWhenIdIsAbsentFromSupportedHotelsList() {
        hotelNotFound = true;

        given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().plusDays(35).toString()))
                .post("/travelagent/bookings")
                .then().statusCode(400)
                .body("error", org.hamcrest.Matchers.containsString("Hotel hotel ID 51 was not present in GET /hotels"));

        assertTrue(calls.isEmpty(), "No component booking should be created when the Hotel ID is missing");
    }

    @Test
    void retriesTransientEmptyHotelListsBeforeCreatingTheBooking() {
        hotelCustomerListMissingResponsesRemaining = 1;
        hotelListMissingResponsesRemaining = 1;

        String created = given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().plusDays(36).toString()))
                .post("/travelagent/bookings").then().statusCode(201).extract().asString();
        long aggregateId = io.restassured.path.json.JsonPath.from(created).getLong("id");

        given().delete("/travelagent/bookings/{id}", aggregateId).then().statusCode(204);
        assertEquals(List.of("taxi:create", "hotel:create", "flight:create",
                "flight:cancel", "hotel:cancel", "taxi:cancel"), calls);
    }

    @Test
    void keepsAggregateAndTaxiBookingWhenFlightCancellationFails() {
        long aggregateId = given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().plusDays(34).toString()))
                .post("/travelagent/bookings").then().statusCode(201).extract().jsonPath().getLong("id");
        flightCancelFails = true;

        given().delete("/travelagent/bookings/{id}", aggregateId)
                .then().statusCode(502).body("error", org.hamcrest.Matchers.containsString("Flight"));

        given().get("/travelagent/bookings/{id}", aggregateId).then().statusCode(200);
        assertTaxiBookingPresent(lastTaxiBookingId);
        assertEquals(List.of("taxi:create", "hotel:create", "flight:create",
                "flight:cancel"), calls);
    }

    @Test
    void cancellationContinuesWhenFlightClientThrowsNotFound() {
        String date = LocalDate.now().plusDays(32).toString();
        long aggregateId = given().contentType(MediaType.APPLICATION_JSON).body(bookingRequest(date))
                .post("/travelagent/bookings").then().statusCode(201).extract().jsonPath().getLong("id");
        flightCancelReturnsNotFound = true;

        given().delete("/travelagent/bookings/{id}", aggregateId).then().statusCode(204);

        given().get("/travelagent/bookings/{id}", aggregateId).then().statusCode(404);
        assertEquals(List.of("taxi:create", "hotel:create", "flight:create",
                "flight:cancel", "hotel:cancel", "taxi:cancel"), calls);
    }

    @Test
    void retriesAfterPartialCancellationWhenRemoteBookingsAreAlreadyGone() {
        long aggregateId = given().contentType(MediaType.APPLICATION_JSON)
                .body(bookingRequest(LocalDate.now().plusDays(36).toString()))
                .post("/travelagent/bookings").then().statusCode(201).extract().jsonPath().getLong("id");
        long taxiBookingId = lastTaxiBookingId;
        // Simulate Hotel deleting its booking but returning an error before the
        // response reaches us. Flight is already cancelled; Taxi must stay until retry.
        hotelCancelFails = true;

        given().delete("/travelagent/bookings/{id}", aggregateId)
                .then().statusCode(502).body("error", org.hamcrest.Matchers.containsString("Hotel"));
        given().get("/travelagent/bookings/{id}", aggregateId).then().statusCode(200);
        assertTaxiBookingPresent(taxiBookingId);
        assertEquals(List.of("taxi:create", "hotel:create", "flight:create",
                "flight:cancel", "hotel:cancel"), calls);

        // Both upstream services now report 404 because their first cancellation
        // succeeded. Those responses count as completed cancellation on retry.
        given().delete("/travelagent/bookings/{id}", aggregateId).then().statusCode(204);
        given().get("/travelagent/bookings/{id}", aggregateId).then().statusCode(404);
        assertTaxiBookingAbsent(taxiBookingId);
        assertEquals(List.of("taxi:create", "hotel:create", "flight:create",
                "flight:cancel", "hotel:cancel", "flight:cancel", "hotel:cancel", "taxi:cancel"), calls);
    }

    private Map<String, Object> bookingRequest(String date) {
        Map<String, Object> request = new HashMap<>();
        request.put("customerId", customerId);
        request.put("taxiId", taxiId);
        request.put("hotelId", 51L);
        request.put("flightId", 62L);
        request.put("bookingDate", date);
        return request;
    }

    private void assertTaxiBookingPresent(long id) {
        List<Long> ids = given().get("/bookings/customer/{id}", 1L).then().statusCode(200)
                .extract().jsonPath().getList("id", Long.class);
        assertTrue(ids.contains(id), "Expected Taxi booking " + id + " to remain");
    }

    private void assertTaxiBookingAbsent(long id) {
        List<Long> ids = given().get("/bookings/customer/{id}", 1L).then().statusCode(200)
                .extract().jsonPath().getList("id", Long.class);
        assertTrue(!ids.contains(id), "Expected Taxi booking " + id + " to be cancelled");
    }

    private void installStubClients() {
        QuarkusMock.installMockForType(new TaxiApiClient() {
            @Override public Response createBooking(TaxiBookingRequest request) {
                calls.add("taxi:create");
                Map<String, Object> body = new HashMap<>();
                body.put("customerId", request.getCustomerId());
                body.put("taxiId", request.getTaxiId());
                body.put("date", request.getDate().toString());
                String response = given().contentType(MediaType.APPLICATION_JSON).body(body)
                        .post("/bookings").then().statusCode(201).extract().asString();
                long bookingId = io.restassured.path.json.JsonPath.from(response).getLong("id");
                lastTaxiBookingId = bookingId;
                return Response.status(Response.Status.CREATED)
                        .entity(reference(bookingId)).build();
            }

            @Override public Response cancelBooking(Long id) {
                calls.add("taxi:cancel");
                given().delete("/bookings/{id}", id).then().statusCode(204);
                return Response.noContent().build();
            }
        }, TaxiApiClient.class, RestClient.LITERAL);

        QuarkusMock.installMockForType(new HotelApiClient() {
            @Override public Response getCustomers() {
                if (hotelCustomerListMissingResponsesRemaining > 0) {
                    hotelCustomerListMissingResponsesRemaining--;
                    return Response.ok("[]").build();
                }
                return hotelCustomerNotFound
                        ? Response.ok("[]").build()
                        : Response.ok("[{\"id\":101}]").build();
            }

            @Override public Response getHotels() {
                if (hotelListMissingResponsesRemaining > 0) {
                    hotelListMissingResponsesRemaining--;
                    return Response.ok("[]").build();
                }
                return Response.ok(hotelNotFound ? "[]" : "[{\"id\":51}]").build();
            }

            @Override public Response createBooking(HotelBookingRequest request) {
                calls.add("hotel:create");
                assertEquals(101L, request.getCustomerId());
                assertEquals(51L, request.getHotelId());
                if (hotelBookingNotFoundResponsesRemaining > 0) {
                    hotelBookingNotFoundResponsesRemaining--;
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity("Customer is not on this service instance").build();
                }
                if (hotelFails)
                    return Response.status(Response.Status.CONFLICT).entity("Hotel room is unavailable").build();
                return Response.status(Response.Status.CREATED)
                        .entity(reference(HOTEL_BOOKING_ID)).build();
            }

            @Override public Response cancelBooking(Long id) {
                calls.add("hotel:cancel");
                assertEquals(HOTEL_BOOKING_ID, id);
                if (hotelCancelled) return Response.status(Response.Status.NOT_FOUND).build();
                hotelCancelled = true;
                if (hotelCancelFails)
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Hotel cancellation response lost").build();
                return Response.noContent().build();
            }
        }, HotelApiClient.class, RestClient.LITERAL);

        QuarkusMock.installMockForType(new FlightApiClient() {
            @Override public Response getCustomer(Long id) {
                assertEquals(202L, id);
                if (flightCustomerLookupFailuresRemaining > 0) {
                    flightCustomerLookupFailuresRemaining--;
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity("Customer is not on this service instance").build();
                }
                return Response.ok("{}").build();
            }

            @Override public Response getFlight(Long id) {
                assertEquals(62L, id);
                return Response.ok("{}").build();
            }

            @Override public Response createBooking(FlightBookingRequest request) {
                calls.add("flight:create");
                assertEquals(202L, request.getCustomerId());
                assertEquals(62L, request.getFlightId());
                assertTrue(request.getBookingDate().isAfter(LocalDate.now()));
                if (flightNotFound) {
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity("No flight with the requested ID").build();
                }
                if (flightFails) throw new WebApplicationException(Response.status(Response.Status.CONFLICT).build());
                return Response.status(Response.Status.CREATED)
                        .entity(reference(FLIGHT_BOOKING_ID)).build();
            }

            @Override public Response cancelBooking(Long id) {
                calls.add("flight:cancel");
                assertEquals(FLIGHT_BOOKING_ID, id);
                if (flightCancelFails)
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Flight cancellation failed").build();
                if (flightCancelReturnsNotFound)
                    throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
                if (flightCancelled) return Response.status(Response.Status.NOT_FOUND).build();
                flightCancelled = true;
                return Response.noContent().build();
            }
        }, FlightApiClient.class, RestClient.LITERAL);
    }

    private RemoteBookingReference reference(long id) {
        RemoteBookingReference reference = new RemoteBookingReference();
        reference.setId(id);
        return reference;
    }
}
