package uk.ac.newcastle.enterprisemiddleware.travelagent;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.time.LocalDate;

/** Aggregate booking request. customerId belongs to this service; commodity IDs belong to their services. */
public class TravelAgentBookingRequest {
    @NotNull @Positive private Long customerId;
    @NotNull @Positive private Long taxiId;
    @NotNull @Positive private Long hotelId;
    @NotNull @Positive private Long flightId;
    @NotNull @Future private LocalDate bookingDate;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getTaxiId() { return taxiId; }
    public void setTaxiId(Long taxiId) { this.taxiId = taxiId; }
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
    public Long getFlightId() { return flightId; }
    public void setFlightId(Long flightId) { this.flightId = flightId; }
    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }
}
