package uk.ac.newcastle.enterprisemiddleware.taxi;

import uk.ac.newcastle.enterprisemiddleware.util.ErrorMessage;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;

@Provider
public class TaxiConflictExceptionMapper implements ExceptionMapper<TaxiConflictException> {
    @Override public Response toResponse(TaxiConflictException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorMessage(exception.getMessage(), Collections.emptyMap())).build();
    }
}
