package uk.ac.newcastle.enterprisemiddleware.travelagent;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
@RegisterRestClient(configKey = "hotel-api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface HotelApiClient {
    // The deployed Hotel contract exposes collection GETs, not individual GET-by-ID routes.
    // The caller checks the returned objects for the requested ID.
    @GET @Path("/customers") Response getCustomers();
    @GET @Path("/hotels") Response getHotels();
    @POST @Path("/bookings") Response createBooking(HotelBookingRequest request);
    @DELETE @Path("/bookings/{id:[0-9]+}") Response cancelBooking(@PathParam("id") Long id);
}
