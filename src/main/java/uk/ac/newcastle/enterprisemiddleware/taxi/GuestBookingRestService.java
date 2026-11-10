package uk.ac.newcastle.enterprisemiddleware.taxi;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import uk.ac.newcastle.enterprisemiddleware.util.ErrorMessage;
import uk.ac.newcastle.enterprisemiddleware.util.RestServiceException;
import javax.inject.Inject;
import javax.transaction.Status;
import javax.transaction.UserTransaction;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;

@Path("/guest-bookings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Guest bookings", description = "Atomically creates a customer and their booking")
public class GuestBookingRestService {
    @Inject UserTransaction transaction;
    @Inject CustomerService customers;
    @Inject TaxiService taxis;
    @Inject BookingService bookings;

    @POST
    @Operation(summary = "Create guest booking", description = "Creates the new customer and their booking in one manually managed JTA transaction.")
    @APIResponse(responseCode = "201", description = "Customer and booking created atomically")
    @APIResponse(responseCode = "400", description = "Customer or booking data is invalid")
    @APIResponse(responseCode = "404", description = "Referenced taxi was not found")
    @APIResponse(responseCode = "409", description = "Email or taxi/date pair conflicts with an existing record")
    public Response createGuestBooking(GuestBooking request) {
        if (request == null || request.getCustomer() == null || request.getBooking() == null)
            throw new RestServiceException("Customer and booking are required", Response.Status.BAD_REQUEST);
        try {
            transaction.begin();
            Customer customer = request.getCustomer();
            customer.setId(null);
            Customer createdCustomer = customers.create(customer);

            Booking booking = request.getBooking();
            booking.setId(null);
            if (booking.getTaxi() == null || booking.getTaxi().getId() == null)
                throw new RestServiceException("A taxi id is required", Response.Status.BAD_REQUEST);
            Taxi taxi = taxis.findById(booking.getTaxi().getId());
            if (taxi == null) throw new TaxiNotFoundException("Taxi " + booking.getTaxi().getId() + " was not found");
            booking.setTaxi(taxi);
            booking.setCustomer(createdCustomer);
            Booking createdBooking = bookings.create(booking);
            transaction.commit();
            return Response.status(Response.Status.CREATED).entity(createdBooking).build();
        } catch (TaxiConflictException | TaxiNotFoundException | ConstraintViolationException e) {
            rollbackQuietly();
            throw e;
        } catch (RestServiceException e) {
            rollbackQuietly();
            throw e;
        } catch (Exception e) {
            rollbackQuietly();
            throw new RestServiceException("Guest booking could not be completed", e);
        }
    }

    private void rollbackQuietly() {
        try {
            if (transaction.getStatus() == Status.STATUS_ACTIVE || transaction.getStatus() == Status.STATUS_MARKED_ROLLBACK)
                transaction.rollback();
        } catch (Exception ignored) {
            // Preserve the original failure; a completed/failed transaction cannot be rolled back again.
        }
    }
}
