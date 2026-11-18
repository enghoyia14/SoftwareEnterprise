package uk.ac.newcastle.enterprisemiddleware.travelagent;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.ac.newcastle.enterprisemiddleware.taxi.Booking;
import uk.ac.newcastle.enterprisemiddleware.taxi.Customer;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

/** Locally stored record of an aggregate trip and the bookings made in the other services. */
@Entity
@Table(name = "travel_agent_booking")
@TableGenerator(name = "travelAgentBookingIds", table = "hibernate_sequences",
        pkColumnName = "sequence_name", valueColumnName = "next_val",
        pkColumnValue = "travel_agent_booking", allocationSize = 1)
public class TravelAgentBooking implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "travelAgentBookingIds")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    /** The actual customer represented in this TravelAgent service. */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** The Taxi booking is local to this service; Flight and Hotel IDs are held below. */
    @OneToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "taxi_booking_id", nullable = false, unique = true)
    private Booking taxiBooking;

    @Column(name = "flight_booking_id", nullable = false)
    private Long flightBookingId;
    @Column(name = "hotel_booking_id", nullable = false)
    private Long hotelBookingId;
    @Column(name = "flight_id", nullable = false)
    private Long flightId;
    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;
    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Booking getTaxiBooking() { return taxiBooking; }
    public void setTaxiBooking(Booking taxiBooking) { this.taxiBooking = taxiBooking; }
    public Long getFlightBookingId() { return flightBookingId; }
    public void setFlightBookingId(Long flightBookingId) { this.flightBookingId = flightBookingId; }
    public Long getHotelBookingId() { return hotelBookingId; }
    public void setHotelBookingId(Long hotelBookingId) { this.hotelBookingId = hotelBookingId; }
    public Long getFlightId() { return flightId; }
    public void setFlightId(Long flightId) { this.flightId = flightId; }
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }
}
