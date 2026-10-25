package com.adapteraspring.repository;

import com.adapteraspring.entity.DialogEvents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



@Repository
public interface DialogEventRepository extends JpaRepository<DialogEvents, Long> {

}
