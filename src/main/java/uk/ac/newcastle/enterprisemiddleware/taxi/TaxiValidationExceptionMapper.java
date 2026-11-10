package uk.ac.newcastle.enterprisemiddleware.taxi;

import uk.ac.newcastle.enterprisemiddleware.util.ErrorMessage;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class TaxiValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override public Response toResponse(ConstraintViolationException exception) {
        Map<String, String> reasons = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations())
            reasons.put(violation.getPropertyPath().toString(), violation.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorMessage("Invalid request", reasons)).build();
    }
}
