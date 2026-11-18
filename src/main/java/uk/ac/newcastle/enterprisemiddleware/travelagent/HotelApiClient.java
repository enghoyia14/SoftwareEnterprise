package uk.ac.newcastle.enterprisemiddleware.travelagent;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/bookings")
@RegisterRestClient(configKey = "hotel-api")
@Consumes(MediaType.APPLICATION_JSON)
public interface HotelApiClient {
    @POST Response createBooking(HotelBookingRequest request);
    @DELETE @Path("/{id:[0-9]+}") Response cancelBooking(@PathParam("id") Long id);
}
