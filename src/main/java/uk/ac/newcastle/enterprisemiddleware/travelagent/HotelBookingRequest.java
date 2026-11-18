package uk.ac.newcastle.enterprisemiddleware.travelagent;

import java.time.LocalDate;

/** Booking body documented by the reachable Hotel OpenAPI contract. */
public class HotelBookingRequest {
    private Long customerId;
    private Long hotelId;
    private LocalDate bookingDate;
    public HotelBookingRequest() { }
    public HotelBookingRequest(Long customerId, Long hotelId, LocalDate bookingDate) {
        this.customerId = customerId; this.hotelId = hotelId; this.bookingDate = bookingDate;
    }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }
}
