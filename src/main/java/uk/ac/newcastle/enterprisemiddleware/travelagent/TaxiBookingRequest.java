package uk.ac.newcastle.enterprisemiddleware.travelagent;

import java.time.LocalDate;

/** Request contract of this project's Taxi API. */
public class TaxiBookingRequest {
    private Long customerId;
    private Long taxiId;
    private LocalDate date;
    public TaxiBookingRequest() { }
    public TaxiBookingRequest(Long customerId, Long taxiId, LocalDate date) {
        this.customerId = customerId; this.taxiId = taxiId; this.date = date;
    }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getTaxiId() { return taxiId; }
    public void setTaxiId(Long taxiId) { this.taxiId = taxiId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}
