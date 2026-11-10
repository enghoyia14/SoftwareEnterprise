package uk.ac.newcastle.enterprisemiddleware.taxi;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.List;
import java.util.Set;

@Dependent
public class TaxiService {
    @Inject TaxiRepository repository;
    @Inject Validator validator;

    public List<Taxi> findAll() { return repository.findAll(); }
    public Taxi findById(Long id) { return repository.findById(id); }
    Taxi create(Taxi taxi) {
        Set<ConstraintViolation<Taxi>> violations = validator.validate(taxi);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if (repository.findByRegistration(taxi.getRegistration()) != null)
            throw new TaxiConflictException("A taxi with this registration already exists");
        return repository.create(taxi);
    }
    void delete(Taxi taxi) { repository.delete(taxi); }
}
