package uk.ac.newcastle.enterprisemiddleware.taxi;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.List;
import java.util.Set;

@Dependent
public class BookingService {
    @Inject BookingRepository repository;
    @Inject CustomerRepository customers;
    @Inject TaxiRepository taxis;
    @Inject Validator validator;

    public List<Booking> findByCustomer(Long customerId) { return repository.findByCustomer(customerId); }
    public Booking findById(Long id) { return repository.findById(id); }
    public Booking create(BookingRequest request) {
        Set<ConstraintViolation<BookingRequest>> requestViolations = validator.validate(request);
        if (!requestViolations.isEmpty()) throw new ConstraintViolationException(requestViolations);
        Customer customer = customers.findById(request.getCustomerId());
        if (customer == null) throw new TaxiNotFoundException("Customer " + request.getCustomerId() + " was not found");
        Taxi taxi = taxis.findById(request.getTaxiId());
        if (taxi == null) throw new TaxiNotFoundException("Taxi " + request.getTaxiId() + " was not found");
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setTaxi(taxi);
        booking.setDate(request.getDate());
        return create(booking);
    }
    Booking create(Booking booking) {
        Set<ConstraintViolation<Booking>> violations = validator.validate(booking);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if (repository.findByTaxiAndDate(booking.getTaxi().getId(), booking.getDate()) != null)
            throw new TaxiConflictException("This taxi is already booked for that date");
        return repository.create(booking);
    }
    void delete(Booking booking) { repository.delete(booking); }
}
