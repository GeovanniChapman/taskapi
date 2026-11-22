package com.example.taskapi.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

import com.example.taskapi.entity.Task;

import java.util.List;

public final class ApiModels {

	private ApiModels() {
	}

	public record RegisterRequest(

			@NotBlank @Email @Size(max = 254) String email,

			@NotBlank @Size(min = 12, max = 72) String password) {
	}

	public record LoginRequest(

			@NotBlank @Email @Size(max = 254) String email,

			@NotBlank @Size(max = 72) String password) {
	}

	public record UserResponse(
			UUID id,
			String email) {
	}

	public record TokenResponse(
			String accessToken,
			String tokenType,
			long expiresIn) {

	}

	public record CreateTaskRequest(
			@NotBlank @Size(max = 200) String title,
			@NotNull Boolean completed) {
	}

	public record UpdateTaskRequest(
			@NotBlank @Size(max = 200) String title,
			@NotNull Boolean completed,
			@NotNull @PositiveOrZero Long version) {
	}

	public record TaskResponse(
			UUID id,
			String title,
			boolean completed,
			Long version) {
		public static TaskResponse from(Task task) {
			return new TaskResponse(
					task.getId(),
					task.getTitle(),
					task.isCompleted(),
					task.getVersion());
		}
	}

	public record PageResponse<T>(
			List<T> content,
			int page,
			int size,
			long totalElements,
			int totalPages) {
	}
}
