package uk.ac.newcastle.enterprisemiddleware.taxi;

import javax.persistence.*;
import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "booking", uniqueConstraints = @UniqueConstraint(columnNames = {"taxi_id", "booking_date"}))
@TableGenerator(name = "bookingIds", table = "hibernate_sequences", pkColumnName = "sequence_name",
        valueColumnName = "next_val", pkColumnValue = "booking", allocationSize = 1)
public class Booking implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.TABLE, generator = "bookingIds")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    @NotNull
    private Customer customer;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "taxi_id", nullable = false)
    @NotNull
    private Taxi taxi;

    @NotNull @Future
    @Column(name = "booking_date", nullable = false)
    private LocalDate date;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Taxi getTaxi() { return taxi; }
    public void setTaxi(Taxi taxi) { this.taxi = taxi; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}
