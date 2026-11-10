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

@Path("/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Customers", description = "Create, list and delete taxi booking customers")
public class CustomerRestService {
    @Inject CustomerService service;
    @GET @Operation(summary = "List customers", description = "Returns all customer records.")
    @APIResponse(responseCode = "200", description = "Customer collection returned")
    public List<Customer> listCustomers() { return service.findAll(); }

    @POST @Transactional
    @Operation(summary = "Create customer", description = "Creates a customer. The database assigns the id.")
    @APIResponse(responseCode = "201", description = "Customer created")
    @APIResponse(responseCode = "400", description = "Required data or validation rule failed")
    @APIResponse(responseCode = "409", description = "Customer email is already in use")
    public Response createCustomer(Customer customer) {
        if (customer == null) throw new RestServiceException("Customer body is required", Response.Status.BAD_REQUEST);
        customer.setId(null);
        return Response.status(Response.Status.CREATED).entity(service.create(customer)).build();
    }

    @DELETE @Path("/{id:[0-9]+}") @Transactional
    @Operation(summary = "Delete customer", description = "Deletes a customer and cascades to their bookings.")
    @APIResponse(responseCode = "204", description = "Customer and associated bookings deleted")
    @APIResponse(responseCode = "404", description = "Customer was not found")
    public Response deleteCustomer(@PathParam("id") Long id) {
        Customer customer = service.findById(id);
        if (customer == null) throw new TaxiNotFoundException("Customer " + id + " was not found");
        service.delete(customer);
        return Response.noContent().build();
    }
}
