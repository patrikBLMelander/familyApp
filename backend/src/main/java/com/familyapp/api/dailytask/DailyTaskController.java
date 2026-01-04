package com.familyapp.api.dailytask;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.dailytask.DailyTaskService;
import com.familyapp.domain.dailytask.DailyTask;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/daily-tasks")
public class DailyTaskController {

    private final DailyTaskService service;
    private final FamilyMemberService memberService;

    public DailyTaskController(DailyTaskService service, FamilyMemberService memberService) {
        this.service = service;
        this.memberService = memberService;
    }

    @GetMapping("/today")
    public List<DailyTaskWithCompletionResponse> getTasksForToday(
            @RequestParam(value = "memberId", required = false) UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID familyId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                familyId = member.familyId();
            } catch (IllegalArgumentException e) {
                // Invalid token, continue without filtering
            }
        }
        return service.getTasksForToday(memberId, familyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping
    public List<DailyTaskResponse> getAllTasks(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID familyId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                familyId = member.familyId();
            } catch (IllegalArgumentException e) {
                // Invalid token, continue without filtering
            }
        }
        return service.getAllTasks(familyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DailyTaskResponse createTask(
            @RequestBody CreateDailyTaskRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID familyId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                familyId = member.familyId();
            } catch (IllegalArgumentException e) {
                // Invalid token, continue without familyId
            }
        }
        var task = service.createTask(
                request.name(),
                request.description(),
                request.daysOfWeek(),
                request.memberIds() != null ? request.memberIds() : Set.of(),
                familyId,
                request.isRequired() != null ? request.isRequired() : true,
                request.xpPoints() != null ? request.xpPoints() : 1
        );
        return toResponse(task);
    }

    @PatchMapping("/{taskId}")
    public DailyTaskResponse updateTask(
            @PathVariable("taskId") UUID taskId,
            @RequestBody UpdateDailyTaskRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        // Note: updateTask doesn't change family, so we don't need to pass familyId
        var task = service.updateTask(
                taskId,
                request.name(),
                request.description(),
                request.daysOfWeek(),
                request.memberIds() != null ? request.memberIds() : Set.of(),
                request.isRequired() != null ? request.isRequired() : true,
                request.xpPoints() != null ? request.xpPoints() : 1
        );
        return toResponse(task);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable("taskId") UUID taskId) {
        service.deleteTask(taskId);
    }

    @PostMapping("/{taskId}/toggle")
    public void toggleTaskCompletion(
            @PathVariable("taskId") UUID taskId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken,
            @RequestParam(value = "memberId", required = false) UUID memberIdParam
    ) {
        UUID memberId = null;
        // If memberId is provided as parameter, use it (for family mode)
        if (memberIdParam != null) {
            memberId = memberIdParam;
        } else if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                memberId = member.id();
            } catch (IllegalArgumentException e) {
                // Invalid token, use admin as default
                memberId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            }
        } else {
            // No token, use admin as default
            memberId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        service.toggleTaskCompletion(taskId, memberId);
    }

    @GetMapping("/today/with-children")
    public List<DailyTaskWithChildrenCompletionResponse> getTasksForTodayWithChildren(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID parentId = null;
        UUID familyId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                parentId = member.id();
                familyId = member.familyId();
            } catch (IllegalArgumentException e) {
                // Invalid token, continue without filtering
            }
        }
        return service.getTasksForTodayWithChildren(parentId, familyId).stream()
                .map(this::toResponseWithChildren)
                .toList();
    }

    @PostMapping("/reorder")
    public List<DailyTaskResponse> reorderTasks(@RequestBody ReorderTasksRequest request) {
        return service.reorderTasks(request.taskIds()).stream()
                .map(this::toResponse)
                .toList();
    }

    private DailyTaskResponse toResponse(DailyTask task) {
        return new DailyTaskResponse(
                task.id(),
                task.name(),
                task.description(),
                task.daysOfWeek(),
                task.memberIds(),
                task.position(),
                task.isRequired(),
                task.xpPoints()
        );
    }

    private DailyTaskWithCompletionResponse toResponse(DailyTaskService.DailyTaskWithCompletion taskWithCompletion) {
        return new DailyTaskWithCompletionResponse(
                toResponse(taskWithCompletion.task()),
                taskWithCompletion.completed()
        );
    }

    private DailyTaskWithChildrenCompletionResponse toResponseWithChildren(
            DailyTaskService.DailyTaskWithChildrenCompletion taskWithChildren
    ) {
        var childCompletions = taskWithChildren.childCompletions().stream()
                .map(cc -> new ChildCompletionResponse(cc.childId(), cc.childName(), cc.completed()))
                .toList();
        
        return new DailyTaskWithChildrenCompletionResponse(
                toResponse(taskWithChildren.task()),
                childCompletions
        );
    }

    public record CreateDailyTaskRequest(
            @NotBlank(message = "Name is required")
            String name,
            String description,
            Set<DailyTask.DayOfWeek> daysOfWeek,
            Set<UUID> memberIds,
            Boolean isRequired,
            Integer xpPoints
    ) {
    }

    public record UpdateDailyTaskRequest(
            @NotBlank(message = "Name is required")
            String name,
            String description,
            Set<DailyTask.DayOfWeek> daysOfWeek,
            Set<UUID> memberIds,
            Boolean isRequired,
            Integer xpPoints
    ) {
    }

    public record DailyTaskResponse(
            UUID id,
            String name,
            String description,
            Set<DailyTask.DayOfWeek> daysOfWeek,
            Set<UUID> memberIds,
            int position,
            boolean isRequired,
            int xpPoints
    ) {
    }

    public record DailyTaskWithCompletionResponse(
            DailyTaskResponse task,
            boolean completed
    ) {
    }

    public record ReorderTasksRequest(
            List<UUID> taskIds
    ) {
    }

    public record ChildCompletionResponse(
            UUID childId,
            String childName,
            boolean completed
    ) {
    }

    public record DailyTaskWithChildrenCompletionResponse(
            DailyTaskResponse task,
            List<ChildCompletionResponse> childCompletions
    ) {
    }
}

