package uk.ac.newcastle.enterprisemiddleware.travelagent;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
@RegisterRestClient(configKey = "flight-api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface FlightApiClient {
    @GET @Path("/customers/{id:[0-9]+}") Response getCustomer(@PathParam("id") Long id);
    @GET @Path("/flights/{id:[0-9]+}") Response getFlight(@PathParam("id") Long id);
    @POST @Path("/bookings") Response createBooking(FlightBookingRequest request);
    @DELETE @Path("/bookings/{id:[0-9]+}") Response cancelBooking(@PathParam("id") Long id);
}
