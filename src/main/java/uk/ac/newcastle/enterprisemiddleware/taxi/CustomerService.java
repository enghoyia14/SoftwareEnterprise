package uk.ac.newcastle.enterprisemiddleware.taxi;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.List;
import java.util.Set;

@Dependent
public class CustomerService {
    @Inject CustomerRepository repository;
    @Inject Validator validator;

    public List<Customer> findAll() { return repository.findAll(); }
    public Customer findById(Long id) { return repository.findById(id); }
    Customer create(Customer customer) {
        validate(customer);
        if (repository.findByEmail(customer.getEmail()) != null)
            throw new TaxiConflictException("A customer with this email already exists");
        return repository.create(customer);
    }
    void delete(Customer customer) { repository.delete(customer); }
    private void validate(Customer customer) {
        Set<ConstraintViolation<Customer>> violations = validator.validate(customer);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    }
}
