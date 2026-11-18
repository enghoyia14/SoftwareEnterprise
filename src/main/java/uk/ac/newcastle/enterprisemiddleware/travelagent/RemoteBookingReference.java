package uk.ac.newcastle.enterprisemiddleware.travelagent;

/** Minimal response shape needed to retain an upstream booking ID. */
public class RemoteBookingReference {
    private Long id;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
