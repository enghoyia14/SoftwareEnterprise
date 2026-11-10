package uk.ac.newcastle.enterprisemiddleware.taxi;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

@RequestScoped
public class BookingRepository {
    @Inject EntityManager em;
    List<Booking> findByCustomer(Long customerId) {
        return em.createQuery("SELECT b FROM Booking b WHERE b.customer.id = :customerId ORDER BY b.date", Booking.class)
                .setParameter("customerId", customerId).getResultList();
    }
    Booking findById(Long id) { return em.find(Booking.class, id); }
    Booking findByTaxiAndDate(Long taxiId, java.time.LocalDate date) {
        List<Booking> matches = em.createQuery("SELECT b FROM Booking b WHERE b.taxi.id = :taxiId AND b.date = :date", Booking.class)
                .setParameter("taxiId", taxiId).setParameter("date", date).setMaxResults(1).getResultList();
        return matches.isEmpty() ? null : matches.get(0);
    }
    Booking create(Booking booking) { em.persist(booking); return booking; }
    void delete(Booking booking) { em.remove(em.contains(booking) ? booking : em.merge(booking)); }
}
