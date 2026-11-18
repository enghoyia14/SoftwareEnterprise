package uk.ac.newcastle.enterprisemiddleware.travelagent;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/travelagent/bookings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "TravelAgent", description = "Coordinates taxi, hotel and flight bookings as one trip")
public class TravelAgentRestService {
    @Inject TravelAgentBookingService service;

    @GET
    @Operation(summary = "List aggregate bookings", description = "Lists all TravelAgent bookings, or filters by this service's customerId.")
    @APIResponse(responseCode = "200", description = "TravelAgent bookings returned")
    @APIResponse(responseCode = "404", description = "The supplied customerId was not found")
    public List<TravelAgentBooking> listBookings(@QueryParam("customerId") Long customerId) {
        return service.findAll(customerId);
    }

    @GET @Path("/{id:[0-9]+}")
    @Operation(summary = "Fetch an aggregate booking")
    @APIResponse(responseCode = "200", description = "TravelAgent booking returned")
    @APIResponse(responseCode = "404", description = "TravelAgent booking was not found")
    public TravelAgentBooking getBooking(@PathParam("id") Long id) { return service.findById(id); }

    @POST
    @Operation(summary = "Book a taxi, hotel and flight together", description = "Books in order: local Taxi, remote Hotel, remote Flight. If any step fails, previously created bookings are cancelled in reverse order.")
    @APIResponse(responseCode = "201", description = "All three bookings succeeded; the aggregate record was saved")
    @APIResponse(responseCode = "400", description = "Invalid request or an underlying service rejected an ID")
    @APIResponse(responseCode = "404", description = "The TravelAgent customer was not found")
    @APIResponse(responseCode = "409", description = "A commodity is already booked for that date; previous bookings were compensated")
    @APIResponse(responseCode = "502", description = "An upstream service failed or compensation could not complete")
    @APIResponse(responseCode = "503", description = "TravelAgent customer IDs have not been configured")
    public Response createBooking(@Valid TravelAgentBookingRequest request) {
        TravelAgentBooking created = service.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @DELETE @Path("/{id:[0-9]+}")
    @Operation(summary = "Cancel an aggregate booking", description = "Cancels Flight, Hotel and Taxi bookings. The aggregate record is kept if a service is unavailable so cancellation can be retried.")
    @APIResponse(responseCode = "204", description = "All component bookings were cancelled")
    @APIResponse(responseCode = "404", description = "TravelAgent booking was not found")
    @APIResponse(responseCode = "502", description = "A connected service could not complete cancellation; retry later")
    public Response cancelBooking(@PathParam("id") Long id) {
        service.cancel(id);
        return Response.noContent().build();
    }
}
