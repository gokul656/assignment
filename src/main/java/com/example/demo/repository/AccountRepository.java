package com.example.demo.repository;

import com.example.demo.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    Optional<Account> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByCountryIgnoreCase(String country);

    // Aggregation (COUNT/GROUP BY) runs in the DB rather than loading every matching Account into
    // memory and grouping in Java; null state/place (no resolved location) falls back to UNKNOWN.
    @Query("""
            select coalesce(a.location.state, 'UNKNOWN') as state,
                   coalesce(a.location.place, 'UNKNOWN') as place,
                   count(a) as count
            from Account a
            where upper(a.country) = upper(:country)
            group by coalesce(a.location.state, 'UNKNOWN'), coalesce(a.location.place, 'UNKNOWN')
            """)
    List<StatePlaceCount> countGroupedByStateAndPlace(@Param("country") String country);
}
