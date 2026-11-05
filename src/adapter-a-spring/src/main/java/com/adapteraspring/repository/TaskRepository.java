package com.adapteraspring.repository;

import com.adapteraspring.entity.Tasks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import com.adapteraspring.enumerates.StateEnum;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface TaskRepository extends JpaRepository<Tasks, UUID> {
    @Query("""
            select t from Tasks t
            where t.state in :states
              and (t.nextRetryAt is null or t.nextRetryAt <= :now)
            """)
    List<Tasks> findEligible(@Param("states") List<StateEnum> states, @Param("now") Instant now);

    List<Tasks> findByState(StateEnum state);
}
