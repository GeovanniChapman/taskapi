package com.example.taskapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskapi.entity.Task;

public interface TaskRepository extends JpaRepository<Task, UUID> {

	Page<Task> findAllByOwnerId(UUID ownerId, Pageable pageable);
	Optional<Task> findByIdAndOwnerId(UUID id, UUID ownerId);
}
