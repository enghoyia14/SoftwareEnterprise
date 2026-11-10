package uk.ac.newcastle.enterprisemiddleware.taxi;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

@RequestScoped
public class TaxiRepository {
    @Inject EntityManager em;
    List<Taxi> findAll() {
        return em.createQuery("SELECT t FROM Taxi t ORDER BY t.registration", Taxi.class).getResultList();
    }
    Taxi findById(Long id) { return em.find(Taxi.class, id); }
    Taxi findByRegistration(String registration) {
        List<Taxi> matches = em.createQuery("SELECT t FROM Taxi t WHERE t.registration = :registration", Taxi.class)
                .setParameter("registration", registration).setMaxResults(1).getResultList();
        return matches.isEmpty() ? null : matches.get(0);
    }
    Taxi create(Taxi taxi) { em.persist(taxi); return taxi; }
    void delete(Taxi taxi) { em.remove(em.contains(taxi) ? taxi : em.merge(taxi)); }
}
