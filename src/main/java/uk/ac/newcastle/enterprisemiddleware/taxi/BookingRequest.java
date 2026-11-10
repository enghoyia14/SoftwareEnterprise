package uk.ac.newcastle.enterprisemiddleware.taxi;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.time.LocalDate;

/** Request shape for a booking: references existing customer/taxi records by id. */
public class BookingRequest {
    @NotNull @Positive
    private Long customerId;
    @NotNull @Positive
    private Long taxiId;
    @NotNull @Future
    private LocalDate date;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getTaxiId() { return taxiId; }
    public void setTaxiId(Long taxiId) { this.taxiId = taxiId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}
