
package uk.ac.newcastle.enterprisemiddleware.travelagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import uk.ac.newcastle.enterprisemiddleware.taxi.Booking;
import uk.ac.newcastle.enterprisemiddleware.taxi.BookingService;
import uk.ac.newcastle.enterprisemiddleware.taxi.Customer;
import uk.ac.newcastle.enterprisemiddleware.taxi.CustomerService;
import uk.ac.newcastle.enterprisemiddleware.taxi.Taxi;
import uk.ac.newcastle.enterprisemiddleware.taxi.TaxiService;
import uk.ac.newcastle.enterprisemiddleware.util.RestServiceException;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Dependent
public class TravelAgentBookingService {

    private static final int REMOTE_NOT_FOUND_ATTEMPTS = 3;

    @Inject
    TravelAgentBookingRepository repository;

    @Inject
    CustomerService customers;

    @Inject
    TaxiService taxis;

    @Inject
    BookingService taxiBookings;

    @Inject
    Validator validator;

    @Inject
    @RestClient
    TaxiApiClient taxiApi;

    @Inject
    @RestClient
    HotelApiClient hotelApi;

    @Inject
    @RestClient
    FlightApiClient flightApi;

    @Inject
    ObjectMapper objectMapper;


    public List<TravelAgentBooking> findAll(Long customerId) {

        if (customerId == null) {
            return repository.findAll();
        }

        if (customers.findById(customerId) == null) {
            throw new RestServiceException(
                    "Customer " + customerId + " was not found",
                    Response.Status.NOT_FOUND
            );
        }

        return repository.findByCustomer(customerId);
    }


    public TravelAgentBooking findById(Long id) {

        TravelAgentBooking booking = repository.findById(id);

        if (booking == null) {
            throw new RestServiceException(
                    "TravelAgent booking " + id + " was not found",
                    Response.Status.NOT_FOUND
            );
        }

        return booking;
    }


    public TravelAgentBooking create(TravelAgentBookingRequest request) {

        if (request == null) {
            throw new RestServiceException(
                    "Booking body is required",
                    Response.Status.BAD_REQUEST
            );
        }

        Set<ConstraintViolation<TravelAgentBookingRequest>> violations =
                validator.validate(request);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        // Use the customer ID supplied in the TravelAgent POST request.
        Long customerId = request.getCustomerId();

        Customer actualCustomer = customers.findById(customerId);

        if (actualCustomer == null) {
            throw new RestServiceException(
                    "TravelAgent customer ID " + customerId + " was not found",
                    Response.Status.BAD_REQUEST
            );
        }

        // Check that the same customer ID exists in the Taxi service.
        requireLocalTaxiRecords(customerId, request.getTaxiId());

        // Check that the same customer ID exists in the Hotel service.
        requireRemoteListRecord(
                hotelApi::getCustomers,
                customerId,
                "Hotel",
                "customer",
                "/customers"
        );

        // Check that the requested Hotel exists.
        requireRemoteListRecord(
                hotelApi::getHotels,
                request.getHotelId(),
                "Hotel",
                "hotel",
                "/hotels"
        );

        // Check that the same customer ID exists in the Flight service.
        requireRemoteRecord(
                flightApi::getCustomer,
                customerId,
                "Flight",
                "customer"
        );

        // Check that the requested Flight exists.
        requireRemoteRecord(
                flightApi::getFlight,
                request.getFlightId(),
                "Flight",
                "flight"
        );


        List<Compensation> completed = new ArrayList<>();

        try {

            // 1. Create Taxi booking using the supplied customer ID.
            Long taxiBookingId = createRemote(
                    () -> taxiApi.createBooking(
                            new TaxiBookingRequest(
                                    customerId,
                                    request.getTaxiId(),
                                    request.getBookingDate()
                            )
                    ),
                    "Taxi"
            );

            completed.add(() -> cancelTaxi(taxiBookingId));

            Booking localTaxiBooking = taxiBookings.findById(taxiBookingId);

            if (localTaxiBooking == null) {
                throw new RestServiceException(
                        "Taxi service returned a booking that could not be read locally",
                        Response.Status.BAD_GATEWAY
                );
            }


            // 2. Create Hotel booking using the same customer ID.
            Long hotelBookingId = createRemote(
                    () -> hotelApi.createBooking(
                            new HotelBookingRequest(
                                    customerId,
                                    request.getHotelId(),
                                    request.getBookingDate()
                            )
                    ),
                    "Hotel"
            );

            completed.add(() -> cancelHotel(hotelBookingId));


            // 3. Create Flight booking using the same customer ID.
            Long flightBookingId = createRemote(
                    () -> flightApi.createBooking(
                            new FlightBookingRequest(
                                    customerId,
                                    request.getFlightId(),
                                    request.getBookingDate()
                            )
                    ),
                    "Flight"
            );

            completed.add(() -> cancelFlight(flightBookingId));


            // Save the completed aggregate booking.
            TravelAgentBooking aggregate = new TravelAgentBooking();

            aggregate.setCustomer(actualCustomer);
            aggregate.setTaxiBooking(localTaxiBooking);
            aggregate.setHotelBookingId(hotelBookingId);
            aggregate.setFlightBookingId(flightBookingId);
            aggregate.setHotelId(request.getHotelId());
            aggregate.setFlightId(request.getFlightId());
            aggregate.setBookingDate(request.getBookingDate());

            return repository.create(aggregate);

        } catch (RuntimeException failure) {

            RuntimeException apiFailure =
                    failure instanceof RestServiceException
                            ? failure
                            : normalize(failure, "TravelAgent");

            List<String> compensationFailures = compensate(completed);

            if (!compensationFailures.isEmpty()) {
                throw new RestServiceException(
                        "Booking failed and one or more earlier bookings could not be cancelled: "
                                + String.join("; ", compensationFailures),
                        Response.Status.BAD_GATEWAY,
                        apiFailure
                );
            }

            throw apiFailure;
        }
    }


    public void cancel(Long id) {

        TravelAgentBooking aggregate = findById(id);

        cancelFlight(aggregate.getFlightBookingId());
        cancelHotel(aggregate.getHotelBookingId());
        cancelTaxi(aggregate.getTaxiBooking().getId());

        repository.deleteById(id);
    }


    private Long createRemote(Supplier<Response> create, String serviceName) {

        for (int attempt = 1; attempt <= REMOTE_NOT_FOUND_ATTEMPTS; attempt++) {

            try (Response result = create.get()) {

                if (result.getStatus() == Response.Status.NOT_FOUND.getStatusCode()
                        && attempt < REMOTE_NOT_FOUND_ATTEMPTS) {
                    continue;
                }

                if (result.getStatusInfo().getFamily()
                        != Response.Status.Family.SUCCESSFUL) {
                    throw responseFailure(serviceName, result);
                }

                RemoteBookingReference booking =
                        result.readEntity(RemoteBookingReference.class);

                if (booking == null || booking.getId() == null) {
                    throw new RestServiceException(
                            serviceName + " service returned success without a JSON booking 'id'",
                            Response.Status.BAD_GATEWAY
                    );
                }

                return booking.getId();

            } catch (RuntimeException failure) {
                throw normalize(failure, serviceName);
            }
        }

        throw new RestServiceException(
                serviceName + " booking request failed after repeated HTTP 404 responses",
                Response.Status.BAD_REQUEST
        );
    }


    private void requireLocalTaxiRecords(Long customerId, Long taxiId) {

        if (customers.findById(customerId) == null) {
            throw new RestServiceException(
                    "Taxi customer ID " + customerId + " was not found",
                    Response.Status.BAD_REQUEST
            );
        }

        Taxi taxi = taxis.findById(taxiId);

        if (taxi == null) {
            throw new RestServiceException(
                    "Taxi ID " + taxiId + " was not found",
                    Response.Status.BAD_REQUEST
            );
        }
    }


    private void requireRemoteRecord(
            java.util.function.Function<Long, Response> lookup,
            Long id,
            String serviceName,
            String resourceName) {

        for (int attempt = 1; attempt <= REMOTE_NOT_FOUND_ATTEMPTS; attempt++) {

            try (Response result = lookup.apply(id)) {

                if (result.getStatusInfo().getFamily()
                        == Response.Status.Family.SUCCESSFUL) {
                    return;
                }

                if (result.getStatus() == Response.Status.NOT_FOUND.getStatusCode()
                        && attempt < REMOTE_NOT_FOUND_ATTEMPTS) {
                    continue;
                }

                int status = result.getStatus();
                String body = responseBody(result);

                Response.Status mapped =
                        status == Response.Status.NOT_FOUND.getStatusCode()
                                || status == Response.Status.BAD_REQUEST.getStatusCode()
                                ? Response.Status.BAD_REQUEST
                                : Response.Status.BAD_GATEWAY;

                throw new RestServiceException(
                        serviceName + " " + resourceName + " ID " + id
                                + " is unavailable (HTTP " + status + ")"
                                + (body.isEmpty() ? "" : ": " + body),
                        mapped
                );

            } catch (RuntimeException failure) {
                throw normalize(
                        failure,
                        serviceName + " " + resourceName + " lookup"
                );
            }
        }

        throw new RestServiceException(
                serviceName + " " + resourceName + " ID " + id
                        + " is unavailable after repeated HTTP 404 responses",
                Response.Status.BAD_REQUEST
        );
    }


    private void requireRemoteListRecord(
            Supplier<Response> lookup,
            Long id,
            String serviceName,
            String resourceName,
            String endpoint) {

        int lastStatus = Response.Status.NOT_FOUND.getStatusCode();

        for (int attempt = 1; attempt <= REMOTE_NOT_FOUND_ATTEMPTS; attempt++) {

            try (Response result = lookup.get()) {

                lastStatus = result.getStatus();

                if (lastStatus == Response.Status.NOT_FOUND.getStatusCode()
                        && attempt < REMOTE_NOT_FOUND_ATTEMPTS) {
                    continue;
                }

                if (result.getStatusInfo().getFamily()
                        != Response.Status.Family.SUCCESSFUL) {
                    throw responseFailure(serviceName, result);
                }

                String body = result.hasEntity()
                        ? result.readEntity(String.class)
                        : "";

                JsonNode records = objectMapper.readTree(body);

                if (records == null || !records.isArray()) {
                    throw new RestServiceException(
                            serviceName + " GET " + endpoint
                                    + " returned HTTP " + result.getStatus()
                                    + " with an invalid list response",
                            Response.Status.BAD_GATEWAY
                    );
                }

                for (JsonNode record : records) {

                    if (record.path("id").canConvertToLong()
                            && record.path("id").asLong() == id) {
                        return;
                    }
                }

            } catch (RestServiceException failure) {
                throw failure;

            } catch (RuntimeException failure) {
                throw normalize(
                        failure,
                        serviceName + " " + resourceName + " list lookup"
                );

            } catch (java.io.IOException failure) {
                throw new RestServiceException(
                        serviceName + " GET " + endpoint
                                + " returned an unreadable JSON response",
                        Response.Status.BAD_GATEWAY,
                        failure
                );
            }
        }

        throw new RestServiceException(
                serviceName + " " + resourceName + " ID " + id
                        + " was not present in GET " + endpoint
                        + " after " + REMOTE_NOT_FOUND_ATTEMPTS
                        + " attempts (last HTTP " + lastStatus + ")",
                Response.Status.BAD_REQUEST
        );
    }


    private void cancelTaxi(Long id) {
        cancel(() -> taxiApi.cancelBooking(id), "Taxi");
    }

    private void cancelHotel(Long id) {
        cancel(() -> hotelApi.cancelBooking(id), "Hotel");
    }

    private void cancelFlight(Long id) {
        cancel(() -> flightApi.cancelBooking(id), "Flight");
    }


    private void cancel(Supplier<Response> cancellation, String serviceName) {

        try (Response result = cancellation.get()) {

            if (result.getStatusInfo().getFamily()
                    != Response.Status.Family.SUCCESSFUL
                    && result.getStatus() != Response.Status.NOT_FOUND.getStatusCode()) {
                throw responseFailure(serviceName, result);
            }

        } catch (WebApplicationException failure) {

            Response response = failure.getResponse();

            if (response != null
                    && response.getStatus()
                    == Response.Status.NOT_FOUND.getStatusCode()) {
                return;
            }

            throw normalize(failure, serviceName + " cancellation");

        } catch (ProcessingException failure) {

            throw new RestServiceException(
                    serviceName + " service could not be reached for cancellation",
                    Response.Status.BAD_GATEWAY,
                    failure
            );
        }
    }


    private RuntimeException responseFailure(
            String serviceName,
            Response response) {

        int status = response.getStatus();

        Response.Status responseStatus =
                status == Response.Status.CONFLICT.getStatusCode()
                        ? Response.Status.CONFLICT
                        : (status == Response.Status.BAD_REQUEST.getStatusCode()
                        || status == Response.Status.NOT_FOUND.getStatusCode())
                        ? Response.Status.BAD_REQUEST
                        : Response.Status.BAD_GATEWAY;

        String body = responseBody(response);

        return new RestServiceException(
                serviceName + " service returned HTTP " + status
                        + (body.isEmpty() ? "" : ": " + body),
                responseStatus
        );
    }


    private RuntimeException normalize(
            RuntimeException failure,
            String serviceName) {

        if (failure instanceof RestServiceException) {
            return failure;
        }

        if (failure instanceof ProcessingException) {
            return new RestServiceException(
                    serviceName + " service could not be reached: "
                            + usefulMessage(failure),
                    Response.Status.BAD_GATEWAY,
                    failure
            );
        }

        if (failure instanceof WebApplicationException) {

            Response response = ((WebApplicationException) failure).getResponse();

            if (response != null) {
                return responseFailure(serviceName, response);
            }
        }

        return new RestServiceException(
                serviceName + " booking request failed: "
                        + usefulMessage(failure),
                Response.Status.BAD_GATEWAY,
                failure
        );
    }


    private String responseBody(Response response) {

        if (response == null || !response.hasEntity()) {
            return "";
        }

        try {

            if (!response.bufferEntity()) {
                return "";
            }

            String body = response.readEntity(String.class);

            if (body == null) {
                return "";
            }

            body = body.trim();

            return body.length() > 1000
                    ? body.substring(0, 1000) + "…"
                    : body;

        } catch (RuntimeException ignored) {
            return "";
        }
    }


    private String usefulMessage(Throwable failure) {

        String message = failure.getMessage();

        return message == null || message.trim().isEmpty()
                ? failure.getClass().getSimpleName()
                : message.trim();
    }


    private List<String> compensate(List<Compensation> completed) {

        List<String> failures = new ArrayList<>();

        Collections.reverse(completed);

        for (Compensation action : completed) {

            try {
                action.cancel();

            } catch (RuntimeException e) {

                failures.add(
                        e.getMessage() == null
                                ? e.getClass().getSimpleName()
                                : e.getMessage()
                );
            }
        }

        return failures;
    }


    @FunctionalInterface
    private interface Compensation {
        void cancel();
    }
}
