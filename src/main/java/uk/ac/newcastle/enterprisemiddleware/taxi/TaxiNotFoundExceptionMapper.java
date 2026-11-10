package uk.ac.newcastle.enterprisemiddleware.taxi;

import uk.ac.newcastle.enterprisemiddleware.util.ErrorMessage;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;

@Provider
public class TaxiNotFoundExceptionMapper implements ExceptionMapper<TaxiNotFoundException> {
    @Override public Response toResponse(TaxiNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorMessage(exception.getMessage(), Collections.emptyMap())).build();
    }
}
