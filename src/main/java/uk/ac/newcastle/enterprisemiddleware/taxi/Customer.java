package uk.ac.newcastle.enterprisemiddleware.taxi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@TableGenerator(name = "customerIds", table = "hibernate_sequences", pkColumnName = "sequence_name",
        valueColumnName = "next_val", pkColumnValue = "customer", allocationSize = 1)
public class Customer implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.TABLE, generator = "customerIds")
    private Long id;

    @NotBlank @Size(max = 49)
    @Pattern(regexp = "^[\\p{L}]+$", message = "Name must contain letters only")
    @Column(nullable = false)
    private String name;

    @NotBlank @Email
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Pattern(regexp = "^0[0-9]{10}$", message = "Phone number must start with 0 and contain 11 digits")
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private List<Booking> bookings = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
