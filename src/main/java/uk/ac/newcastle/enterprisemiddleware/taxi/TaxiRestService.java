package uk.ac.newcastle.enterprisemiddleware.taxi;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import uk.ac.newcastle.enterprisemiddleware.util.RestServiceException;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/taxis")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Taxis", description = "Create, list and delete taxis available for booking")
public class TaxiRestService {
    @Inject TaxiService service;
    @GET @Operation(summary = "List taxis", description = "Returns all taxi records.")
    @APIResponse(responseCode = "200", description = "Taxi collection returned")
    public List<Taxi> listTaxis() { return service.findAll(); }

    @POST @Transactional
    @Operation(summary = "Create taxi", description = "Creates a taxi. The database assigns the id.")
    @APIResponse(responseCode = "201", description = "Taxi created")
    @APIResponse(responseCode = "400", description = "Required data or validation rule failed")
    @APIResponse(responseCode = "409", description = "Taxi registration is already in use")
    public Response createTaxi(Taxi taxi) {
        if (taxi == null) throw new RestServiceException("Taxi body is required", Response.Status.BAD_REQUEST);
        taxi.setId(null);
        return Response.status(Response.Status.CREATED).entity(service.create(taxi)).build();
    }

    @DELETE @Path("/{id:[0-9]+}") @Transactional
    @Operation(summary = "Delete taxi", description = "Deletes a taxi and its bookings.")
    @APIResponse(responseCode = "204", description = "Taxi and associated bookings deleted")
    @APIResponse(responseCode = "404", description = "Taxi was not found")
    public Response deleteTaxi(@PathParam("id") Long id) {
        Taxi taxi = service.findById(id);
        if (taxi == null) throw new TaxiNotFoundException("Taxi " + id + " was not found");
        service.delete(taxi);
        return Response.noContent().build();
    }
}
