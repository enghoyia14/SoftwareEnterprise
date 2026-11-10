package uk.ac.newcastle.enterprisemiddleware.taxi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "taxi", uniqueConstraints = @UniqueConstraint(columnNames = "registration"))
@TableGenerator(name = "taxiIds", table = "hibernate_sequences", pkColumnName = "sequence_name",
        valueColumnName = "next_val", pkColumnValue = "taxi", allocationSize = 1)
public class Taxi implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.TABLE, generator = "taxiIds")
    private Long id;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9]{7}$", message = "Registration must be 7 alphanumeric characters")
    @Column(nullable = false, unique = true)
    private String registration;

    @Min(2) @Max(20)
    @Column(name = "number_of_seats", nullable = false)
    private int numberOfSeats;

    @OneToMany(mappedBy = "taxi", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private List<Booking> bookings = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRegistration() { return registration; }
    public void setRegistration(String registration) { this.registration = registration; }
    public int getNumberOfSeats() { return numberOfSeats; }
    public void setNumberOfSeats(int numberOfSeats) { this.numberOfSeats = numberOfSeats; }
}
