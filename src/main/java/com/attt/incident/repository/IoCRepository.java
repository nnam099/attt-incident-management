package com.attt.incident.repository;

import com.attt.incident.entity.IoC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IoCRepository extends JpaRepository<IoC, Long> {
}
