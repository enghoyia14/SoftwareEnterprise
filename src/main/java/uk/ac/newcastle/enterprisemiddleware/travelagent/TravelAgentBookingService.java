package uk.ac.newcastle.enterprisemiddleware.travelagent;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import uk.ac.newcastle.enterprisemiddleware.taxi.Booking;
import uk.ac.newcastle.enterprisemiddleware.taxi.BookingService;
import uk.ac.newcastle.enterprisemiddleware.taxi.Customer;
import uk.ac.newcastle.enterprisemiddleware.taxi.CustomerService;
import uk.ac.newcastle.enterprisemiddleware.util.RestServiceException;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Dependent
public class TravelAgentBookingService {
    @Inject TravelAgentBookingRepository repository;
    @Inject CustomerService customers;
    @Inject BookingService taxiBookings;
    @Inject Validator validator;
    @Inject @RestClient TaxiApiClient taxiApi;
    @Inject @RestClient HotelApiClient hotelApi;
    @Inject @RestClient FlightApiClient flightApi;

    @ConfigProperty(name = "travelagent.taxi-customer-id", defaultValue = "-1") long taxiCustomerId;
    @ConfigProperty(name = "travelagent.hotel-customer-id", defaultValue = "-1") long hotelCustomerId;
    @ConfigProperty(name = "travelagent.flight-customer-id", defaultValue = "-1") long flightCustomerId;

    public List<TravelAgentBooking> findAll(Long customerId) {
        if (customerId == null) return repository.findAll();
        if (customers.findById(customerId) == null)
            throw new RestServiceException("Customer " + customerId + " was not found", Response.Status.NOT_FOUND);
        return repository.findByCustomer(customerId);
    }

    public TravelAgentBooking findById(Long id) {
        TravelAgentBooking booking = repository.findById(id);
        if (booking == null) throw new RestServiceException("TravelAgent booking " + id + " was not found", Response.Status.NOT_FOUND);
        return booking;
    }

    public TravelAgentBooking create(TravelAgentBookingRequest request) {
        if (request == null) throw new RestServiceException("Booking body is required", Response.Status.BAD_REQUEST);
        Set<ConstraintViolation<TravelAgentBookingRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        Customer actualCustomer = customers.findById(request.getCustomerId());
        if (actualCustomer == null)
            throw new RestServiceException("Customer " + request.getCustomerId() + " was not found", Response.Status.BAD_REQUEST);
        if (taxiCustomerId <= 0 || hotelCustomerId <= 0 || flightCustomerId <= 0)
            throw new RestServiceException("TravelAgent customer IDs must be configured for the Taxi, Hotel and Flight services",
                    Response.Status.SERVICE_UNAVAILABLE);

        List<Compensation> completed = new ArrayList<>();
        try {
            Long taxiBookingId = createRemote(taxiApi.createBooking(
                    new TaxiBookingRequest(taxiCustomerId, request.getTaxiId(), request.getBookingDate())), "Taxi");
            completed.add(() -> cancelTaxi(taxiBookingId));
            Booking localTaxiBooking = taxiBookings.findById(taxiBookingId);
            if (localTaxiBooking == null)
                throw new RestServiceException("Taxi service returned a booking that could not be read locally",
                        Response.Status.BAD_GATEWAY);

            Long hotelBookingId = createRemote(hotelApi.createBooking(
                    new HotelBookingRequest(hotelCustomerId, request.getHotelId(), request.getBookingDate())), "Hotel");
            completed.add(() -> cancelHotel(hotelBookingId));

            Long flightBookingId = createRemote(flightApi.createBooking(
                    new FlightBookingRequest(flightCustomerId, request.getFlightId(), request.getBookingDate())), "Flight");
            completed.add(() -> cancelFlight(flightBookingId));

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
            RuntimeException apiFailure = normalize(failure);
            List<String> compensationFailures = compensate(completed);
            if (!compensationFailures.isEmpty())
                throw new RestServiceException("Booking failed and one or more earlier bookings could not be cancelled: "
                        + String.join("; ", compensationFailures), Response.Status.BAD_GATEWAY, apiFailure);
            throw apiFailure;
        }
    }

    public void cancel(Long id) {
        TravelAgentBooking aggregate = findById(id);
        // Keep the aggregate record until all cancellations succeed; a retry treats upstream 404 as already cancelled.
        cancelFlight(aggregate.getFlightBookingId());
        cancelHotel(aggregate.getHotelBookingId());
        cancelTaxi(aggregate.getTaxiBooking().getId());
        repository.delete(aggregate);
    }

    private Long createRemote(Response response, String serviceName) {
        try (Response result = response) {
            if (result.getStatus() != Response.Status.CREATED.getStatusCode())
                throw responseFailure(serviceName, result.getStatus());
            RemoteBookingReference booking = result.readEntity(RemoteBookingReference.class);
            if (booking == null || booking.getId() == null)
                throw new RestServiceException(serviceName + " service returned no booking ID", Response.Status.BAD_GATEWAY);
            return booking.getId();
        }
    }

    private void cancelTaxi(Long id) { cancel(taxiApi.cancelBooking(id), "Taxi"); }
    private void cancelHotel(Long id) { cancel(hotelApi.cancelBooking(id), "Hotel"); }
    private void cancelFlight(Long id) { cancel(flightApi.cancelBooking(id), "Flight"); }

    private void cancel(Response response, String serviceName) {
        try (Response result = response) {
            if (result.getStatus() != Response.Status.NO_CONTENT.getStatusCode()
                    && result.getStatus() != Response.Status.NOT_FOUND.getStatusCode())
                throw responseFailure(serviceName, result.getStatus());
        }
    }

    private RuntimeException responseFailure(String serviceName, int status) {
        Response.Status responseStatus = status == Response.Status.CONFLICT.getStatusCode()
                ? Response.Status.CONFLICT
                : (status == Response.Status.BAD_REQUEST.getStatusCode() || status == Response.Status.NOT_FOUND.getStatusCode())
                    ? Response.Status.BAD_REQUEST : Response.Status.BAD_GATEWAY;
        return new RestServiceException(serviceName + " service returned HTTP " + status, responseStatus);
    }

    private RuntimeException normalize(RuntimeException failure) {
        if (failure instanceof RestServiceException) return failure;
        if (failure instanceof ProcessingException)
            return new RestServiceException("A connected booking service could not be reached", Response.Status.BAD_GATEWAY, failure);
        return new RestServiceException("TravelAgent could not complete the booking", failure);
    }

    private List<String> compensate(List<Compensation> completed) {
        List<String> failures = new ArrayList<>();
        Collections.reverse(completed);
        for (Compensation action : completed) {
            try { action.cancel(); }
            catch (RuntimeException e) { failures.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
        }
        return failures;
    }

    @FunctionalInterface
    private interface Compensation { void cancel(); }
}
