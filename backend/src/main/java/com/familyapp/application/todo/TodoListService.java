package com.familyapp.application.todo;

import com.familyapp.domain.todo.TodoItem;
import com.familyapp.domain.todo.TodoList;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import com.familyapp.infrastructure.todo.TodoItemEntity;
import com.familyapp.infrastructure.todo.TodoItemJpaRepository;
import com.familyapp.infrastructure.todo.TodoListEntity;
import com.familyapp.infrastructure.todo.TodoListJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TodoListService {

    private final TodoListJpaRepository listRepository;
    private final TodoItemJpaRepository itemRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final FamilyJpaRepository familyRepository;

    public TodoListService(
            TodoListJpaRepository listRepository,
            TodoItemJpaRepository itemRepository,
            FamilyMemberJpaRepository memberRepository,
            FamilyJpaRepository familyRepository
    ) {
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
    }

    @Transactional(readOnly = true)
    public List<TodoList> getAllLists(UUID currentUserId, UUID familyId) {
        return listRepository.findAllWithItemsBy().stream()
                .filter(list -> {
                    // Filter by family
                    if (familyId != null && (list.getFamily() == null || !list.getFamily().getId().equals(familyId))) {
                        return false;
                    }
                    // Show public lists to everyone in the family
                    if (!list.isPrivate()) {
                        return true;
                    }
                    // Show private lists only to owner
                    return list.getOwner() != null && list.getOwner().getId().equals(currentUserId);
                })
                .map(this::toDomain)
                .sorted(java.util.Comparator.comparingInt(TodoList::position))
                .toList();
    }

    public TodoList createList(String name, boolean isPrivate, UUID ownerId, UUID familyId) {
        var now = OffsetDateTime.now();
        var entity = new TodoListEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        entity.setColor("green"); // Default color
        entity.setPrivate(isPrivate);
        
        if (ownerId != null) {
            var owner = memberRepository.findById(ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + ownerId));
            entity.setOwner(owner);
        }
        
        if (familyId != null) {
            familyRepository.findById(familyId).ifPresent(entity::setFamily);
        }
        
        var existing = listRepository.findAll();
        var minPosition = existing.stream()
                .mapToInt(TodoListEntity::getPosition)
                .min()
                .orElse(0);
        entity.setPosition(minPosition - 1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        var saved = listRepository.save(entity);
        return toDomain(saved);
    }

    public TodoList updateListName(UUID listId, String name) {
        var list = listRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));
        
        list.setName(name);
        list.setUpdatedAt(OffsetDateTime.now());
        
        var saved = listRepository.save(list);
        return toDomain(saved);
    }

    public TodoList updateListColor(UUID listId, String color) {
        var list = listRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));
        
        list.setColor(color);
        list.setUpdatedAt(OffsetDateTime.now());
        
        var saved = listRepository.save(list);
        return toDomain(saved);
    }

    public TodoList updateListPrivacy(UUID listId, boolean isPrivate) {
        var list = listRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));
        
        list.setPrivate(isPrivate);
        list.setUpdatedAt(OffsetDateTime.now());
        
        var saved = listRepository.save(list);
        return toDomain(saved);
    }

    public TodoList addItem(UUID listId, String description) {
        var list = listRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));

        var item = new TodoItemEntity();
        item.setId(UUID.randomUUID());
        item.setList(list);
        item.setDescription(description);
        item.setDone(false);
        // Lägg nya uppgifter längst upp i listan.
        // Vi använder position där lägre tal betyder högre upp.
        var currentItems = list.getItems();
        var minPosition = currentItems.stream()
                .mapToInt(TodoItemEntity::getPosition)
                .min()
                .orElse(0);
        item.setPosition(minPosition - 1);
        item.setCreatedAt(OffsetDateTime.now());

        list.getItems().add(item);
        list.setUpdatedAt(OffsetDateTime.now());

        var savedList = listRepository.save(list);
        return toDomain(savedList);
    }

    public TodoList toggleItem(UUID listId, UUID itemId) {
        var list = listRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));

        var item = list.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Todo item not found: " + itemId));

        var now = OffsetDateTime.now();
        var done = !item.isDone();

        item.setDone(done);
        item.setCompletedAt(done ? now : null);

        var items = list.getItems();
        if (!done) {
            // När en uppgift återaktiveras: lägg den högt upp bland aktiva.
            var minPosition = items.stream()
                    .mapToInt(TodoItemEntity::getPosition)
                    .min()
                    .orElse(0);
            item.setPosition(minPosition - 1);
        }

        list.setUpdatedAt(now);

        var savedList = listRepository.save(list);
        return toDomain(savedList);
    }

    public TodoList clearDone(UUID listId) {
        var list = listRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));

        list.getItems().removeIf(TodoItemEntity::isDone);
        list.setUpdatedAt(OffsetDateTime.now());

        var savedList = listRepository.save(list);
        return toDomain(savedList);
    }

    public TodoList deleteItem(UUID listId, UUID itemId) {
        var list = listRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));

        var removed = list.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new IllegalArgumentException("Todo item not found: " + itemId);
        }

        list.setUpdatedAt(OffsetDateTime.now());
        var savedList = listRepository.save(list);
        return toDomain(savedList);
    }

    public TodoList reorderItems(UUID listId, List<UUID> orderedItemIds) {
        var list = listRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));

        var idToItem = list.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(TodoItemEntity::getId, i -> i));

        int position = 0;
        for (UUID id : orderedItemIds) {
            var item = idToItem.get(id);
            if (item != null) {
                item.setPosition(position++);
            }
        }

        list.setUpdatedAt(OffsetDateTime.now());
        var savedList = listRepository.save(list);
        return toDomain(savedList);
    }

    public List<TodoList> reorderLists(List<UUID> orderedListIds) {
        var lists = listRepository.findAll();
        var idToList = lists.stream()
                .collect(java.util.stream.Collectors.toMap(TodoListEntity::getId, l -> l));

        int position = 0;
        for (UUID id : orderedListIds) {
            var list = idToList.get(id);
            if (list != null) {
                list.setPosition(position++);
            }
        }

        var saved = listRepository.saveAll(lists);
        return saved.stream()
                .map(this::toDomain)
                .sorted(java.util.Comparator.comparingInt(TodoList::position))
                .toList();
    }

    public void deleteList(UUID listId) {
        var list = listRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Todo list not found: " + listId));
        listRepository.delete(list);
    }

    private TodoList toDomain(TodoListEntity entity) {
        var items = entity.getItems().stream()
                .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
                .map(this::toDomain)
                .toList();

        return new TodoList(
                entity.getId(),
                entity.getName(),
                entity.getPosition(),
                entity.getColor() != null ? entity.getColor() : "green",
                entity.isPrivate(),
                entity.getOwner() != null ? entity.getOwner().getId() : null,
                entity.getFamily() != null ? entity.getFamily().getId() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                items
        );
    }

    private TodoItem toDomain(TodoItemEntity entity) {
        return new TodoItem(
                entity.getId(),
                entity.getDescription(),
                entity.isDone(),
                entity.getPosition(),
                entity.getCreatedAt(),
                entity.getCompletedAt()
        );
    }
}


