package uk.ac.newcastle.enterprisemiddleware.travelagent;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;

@RequestScoped
public class TravelAgentBookingRepository {
    @Inject EntityManager em;

    List<TravelAgentBooking> findAll() {
        return em.createQuery("SELECT b FROM TravelAgentBooking b ORDER BY b.id", TravelAgentBooking.class)
                .getResultList();
    }
    List<TravelAgentBooking> findByCustomer(Long customerId) {
        return em.createQuery("SELECT b FROM TravelAgentBooking b WHERE b.customer.id = :customerId ORDER BY b.id",
                        TravelAgentBooking.class)
                .setParameter("customerId", customerId).getResultList();
    }
    TravelAgentBooking findById(Long id) { return em.find(TravelAgentBooking.class, id); }

    @Transactional
    TravelAgentBooking create(TravelAgentBooking booking) {
        em.persist(booking);
        em.flush();
        return booking;
    }

    @Transactional
    void delete(TravelAgentBooking booking) {
        em.remove(em.contains(booking) ? booking : em.merge(booking));
    }
}
