package com.example.taskapi.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private boolean completed;

	@Version
	@Column(nullable = false)
	private Long version;

	protected Task() {
	}

	public Task(UUID ownerId, String title, boolean completed) {
		this.ownerId = ownerId;
		this.title = title;
		this.completed = completed;
	}

	public void update(String title, boolean completed) {
		this.title = title;
		this.completed = completed;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public String getTitle() {
		return title;
	}

	public boolean isCompleted() {
		return completed;
	}

	public Long getVersion() {
		return version;
	}

}
