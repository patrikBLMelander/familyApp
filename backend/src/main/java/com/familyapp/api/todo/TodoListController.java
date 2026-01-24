package com.familyapp.api.todo;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.application.todo.TodoListService;
import com.familyapp.domain.todo.TodoItem;
import com.familyapp.domain.todo.TodoList;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/todo-lists")
public class TodoListController {

    private final TodoListService service;
    private final FamilyMemberService memberService;

    public TodoListController(TodoListService service, FamilyMemberService memberService) {
        this.service = service;
        this.memberService = memberService;
    }

    @GetMapping
    public List<TodoListResponse> getAll(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID currentUserId = null;
        UUID familyId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                currentUserId = member.id();
                familyId = member.familyId();
            } catch (IllegalArgumentException e) {
                // Invalid token, continue without filtering
            }
        }
        
        return service.getAllLists(currentUserId, familyId).stream()
                .map(TodoListController::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoListResponse createList(
            @RequestBody CreateTodoListRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID ownerId = null;
        UUID familyId = null;
        if (deviceToken != null && !deviceToken.isEmpty()) {
            try {
                var member = memberService.getMemberByDeviceToken(deviceToken);
                ownerId = member.id();
                familyId = member.familyId();
            } catch (IllegalArgumentException e) {
                // Invalid token, continue without owner
            }
        }
        
        var list = service.createList(
                request.name(),
                request.isPrivate() != null ? request.isPrivate() : false,
                ownerId,
                familyId
        );
        return toResponse(list);
    }

    @PatchMapping("/{listId}")
    public TodoListResponse updateListName(
            @PathVariable("listId") UUID listId,
            @RequestBody UpdateTodoListRequest request
    ) {
        var list = service.updateListName(listId, request.name());
        return toResponse(list);
    }

    @PatchMapping("/{listId}/color")
    public TodoListResponse updateListColor(
            @PathVariable("listId") UUID listId,
            @RequestBody UpdateTodoListColorRequest request
    ) {
        var list = service.updateListColor(listId, request.color());
        return toResponse(list);
    }

    @PatchMapping("/{listId}/privacy")
    public TodoListResponse updateListPrivacy(
            @PathVariable("listId") UUID listId,
            @RequestBody UpdateTodoListPrivacyRequest request
    ) {
        var list = service.updateListPrivacy(listId, request.isPrivate());
        return toResponse(list);
    }

    @PostMapping("/{listId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public TodoListResponse addItem(
            @PathVariable("listId") UUID listId,
            @RequestBody CreateTodoItemRequest request
    ) {
        var list = service.addItem(listId, request.description());
        return toResponse(list);
    }

    @PatchMapping("/{listId}/items/{itemId}/toggle")
    public TodoListResponse toggleItem(
            @PathVariable("listId") UUID listId,
            @PathVariable("itemId") UUID itemId
    ) {
        var list = service.toggleItem(listId, itemId);
        return toResponse(list);
    }

    @PatchMapping("/{listId}/items/{itemId}")
    public TodoListResponse updateItem(
            @PathVariable("listId") UUID listId,
            @PathVariable("itemId") UUID itemId,
            @RequestBody UpdateTodoItemRequest request
    ) {
        var list = service.updateItem(listId, itemId, request.description());
        return toResponse(list);
    }

    @DeleteMapping("/{listId}/items/done")
    public TodoListResponse clearDone(@PathVariable("listId") UUID listId) {
        var list = service.clearDone(listId);
        return toResponse(list);
    }

    @DeleteMapping("/{listId}/items/{itemId}")
    public TodoListResponse deleteItem(
            @PathVariable("listId") UUID listId,
            @PathVariable("itemId") UUID itemId
    ) {
        var list = service.deleteItem(listId, itemId);
        return toResponse(list);
    }

    @PostMapping("/{listId}/items/reorder")
    public TodoListResponse reorderItems(
            @PathVariable("listId") UUID listId,
            @RequestBody ReorderItemsRequest request
    ) {
        var list = service.reorderItems(listId, request.itemIds());
        return toResponse(list);
    }

    @PostMapping("/reorder")
    public List<TodoListResponse> reorderLists(
            @RequestBody ReorderListsRequest request
    ) {
        return service.reorderLists(request.listIds()).stream()
                .map(TodoListController::toResponse)
                .toList();
    }

    @DeleteMapping("/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteList(@PathVariable("listId") UUID listId) {
        service.deleteList(listId);
    }

    private static TodoListResponse toResponse(TodoList list) {
        var items = list.items().stream()
                .map(TodoListController::toResponse)
                .toList();

        return new TodoListResponse(
                list.id(),
                list.name(),
                list.position(),
                list.color(),
                list.isPrivate(),
                list.createdAt().toString(),
                list.updatedAt().toString(),
                items
        );
    }

    private static TodoItemResponse toResponse(TodoItem item) {
        return new TodoItemResponse(
                item.id(),
                item.description(),
                item.done(),
                item.position()
        );
    }

    public record CreateTodoListRequest(
            @NotBlank(message = "Name is required")
            String name,
            Boolean isPrivate
    ) {
    }

    public record UpdateTodoListRequest(
            @NotBlank(message = "Name is required")
            String name
    ) {
    }

    public record UpdateTodoListColorRequest(
            @NotBlank(message = "Color is required")
            String color
    ) {
    }

    public record UpdateTodoListPrivacyRequest(
            boolean isPrivate
    ) {
    }

    public record CreateTodoItemRequest(
            @NotBlank(message = "Description is required")
            String description
    ) {
    }

    public record UpdateTodoItemRequest(
            @NotBlank(message = "Description is required")
            String description
    ) {
    }

    public record TodoListResponse(
            UUID id,
            String name,
            int position,
            String color,
            boolean isPrivate,
            String createdAt,
            String updatedAt,
            List<TodoItemResponse> items
    ) {
    }

    public record TodoItemResponse(
            UUID id,
            String description,
            boolean done,
            int position
    ) {
    }

    public record ReorderItemsRequest(
            List<UUID> itemIds
    ) {
    }

    public record ReorderListsRequest(
            List<UUID> listIds
    ) {
    }
}


