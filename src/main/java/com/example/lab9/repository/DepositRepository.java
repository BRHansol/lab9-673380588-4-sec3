package com.example.lab9.repository;

import com.example.lab9.model.DepositTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositRepository extends JpaRepository<DepositTransaction, Long> {
}