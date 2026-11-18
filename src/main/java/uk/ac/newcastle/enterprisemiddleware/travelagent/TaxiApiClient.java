package uk.ac.newcastle.enterprisemiddleware.travelagent;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/bookings")
@RegisterRestClient(configKey = "taxi-api")
@Consumes(MediaType.APPLICATION_JSON)
public interface TaxiApiClient {
    @POST Response createBooking(TaxiBookingRequest request);
    @DELETE @Path("/{id:[0-9]+}") Response cancelBooking(@PathParam("id") Long id);
}
