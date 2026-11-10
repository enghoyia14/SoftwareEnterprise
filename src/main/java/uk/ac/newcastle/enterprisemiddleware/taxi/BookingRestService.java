package uk.ac.newcastle.enterprisemiddleware.taxi;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Bookings", description = "Create, list by customer and cancel taxi bookings")
public class BookingRestService {
    @Inject BookingService service;

    @POST @Transactional
    @Operation(summary = "Create booking", description = "Books one taxi for one customer on a future date; each taxi/date pair is unique.")
    @APIResponse(responseCode = "201", description = "Booking created")
    @APIResponse(responseCode = "400", description = "Invalid request or booking date is not in the future")
    @APIResponse(responseCode = "404", description = "Customer or taxi was not found")
    @APIResponse(responseCode = "409", description = "Taxi is already booked for that date")
    public Response createBooking(@Valid BookingRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.create(request)).build();
    }

    @GET @Path("/customer/{customerId:[0-9]+}")
    @Operation(summary = "List a customer's bookings", description = "Returns bookings for the supplied customer id.")
    @APIResponse(responseCode = "200", description = "Booking collection returned; empty when no bookings exist")
    public List<Booking> listCustomerBookings(@PathParam("customerId") Long customerId) {
        return service.findByCustomer(customerId);
    }

    @DELETE @Path("/{id:[0-9]+}") @Transactional
    @Operation(summary = "Cancel booking", description = "Deletes a booking, freeing the taxi/date slot.")
    @APIResponse(responseCode = "204", description = "Booking cancelled")
    @APIResponse(responseCode = "404", description = "Booking was not found")
    public Response cancelBooking(@PathParam("id") Long id) {
        Booking booking = service.findById(id);
        if (booking == null) throw new TaxiNotFoundException("Booking " + id + " was not found");
        service.delete(booking);
        return Response.noContent().build();
    }
}
