package com.example.taskapi.service;

import com.example.taskapi.dto.ApiModels.CreateTaskRequest;
import com.example.taskapi.dto.ApiModels.PageResponse;
import com.example.taskapi.dto.ApiModels.TaskResponse;
import com.example.taskapi.dto.ApiModels.UpdateTaskRequest;
import com.example.taskapi.entity.Task;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.taskapi.repository.TaskRepository;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository tasks;

    public TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional
    public TaskResponse create(UUID ownerId,
            CreateTaskRequest request) {
        Task task = new Task(ownerId, request.title().strip(), request.completed());

        return TaskResponse.from(tasks.saveAndFlush(task));
    }

    public PageResponse<TaskResponse> list(UUID ownerId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        var result = tasks.findAllByOwnerId(ownerId, pageable);
        return new PageResponse<>(result.getContent().stream().map(TaskResponse::from).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public TaskResponse get(UUID ownerId, UUID id) {
        return TaskResponse.from(findOwnedTask(ownerId, id));
    }

    @Transactional
    public TaskResponse update(UUID ownerId, UUID id, UpdateTaskRequest request) {
        Task task = findOwnedTask(ownerId, id);
        if (!Objects.equals(task.getVersion(), request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task changed: reload it before updating");
        }
        task.update(request.title(), request.completed());
        tasks.flush();

        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        var task = findOwnedTask(ownerId, id);
        tasks.delete(task);
    }

    private Task findOwnedTask(UUID ownerId, UUID id) {
        return tasks.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Task not found"));
    }
}
