package uk.ac.newcastle.enterprisemiddleware.taxi;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

@RequestScoped
public class CustomerRepository {
    @Inject EntityManager em;
    List<Customer> findAll() {
        return em.createQuery("SELECT c FROM Customer c ORDER BY c.name", Customer.class).getResultList();
    }
    Customer findById(Long id) { return em.find(Customer.class, id); }
    Customer findByEmail(String email) {
        List<Customer> matches = em.createQuery("SELECT c FROM Customer c WHERE c.email = :email", Customer.class)
                .setParameter("email", email).setMaxResults(1).getResultList();
        return matches.isEmpty() ? null : matches.get(0);
    }
    Customer create(Customer customer) { em.persist(customer); return customer; }
    void delete(Customer customer) { em.remove(em.contains(customer) ? customer : em.merge(customer)); }
}
