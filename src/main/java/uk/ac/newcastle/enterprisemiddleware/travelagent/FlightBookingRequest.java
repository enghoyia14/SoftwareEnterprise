package uk.ac.newcastle.enterprisemiddleware.travelagent;

import java.time.LocalDate;

/** Booking body documented by the reachable Flight OpenAPI contract. */
public class FlightBookingRequest {
    private Long customerId;
    private Long flightId;
    private LocalDate bookingDate;
    public FlightBookingRequest() { }
    public FlightBookingRequest(Long customerId, Long flightId, LocalDate bookingDate) {
        this.customerId = customerId; this.flightId = flightId; this.bookingDate = bookingDate;
    }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getFlightId() { return flightId; }
    public void setFlightId(Long flightId) { this.flightId = flightId; }
    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }
}
