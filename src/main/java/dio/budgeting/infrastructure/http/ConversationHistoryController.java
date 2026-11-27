package dio.budgeting.infrastructure.http;

import dio.budgeting.infrastructure.persistence.repository.ConversationHistoryRepository;
import dio.budgeting.infrastructure.persistence.repository.ConversationHistoryRepository.Exchange;
import dio.budgeting.infrastructure.persistence.repository.ConversationHistoryRepository.Summary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
public class ConversationHistoryController {

    private final ConversationHistoryRepository history;

    public ConversationHistoryController(ConversationHistoryRepository history) {
        this.history = history;
    }

    @GetMapping
    public Page<Summary> list(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long offset = offset(page, size);
        return pageOf(history.list(userId, offset, size + 1), page, size);
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<Exchange>> messages(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long offset = offset(page, size);
        if (!history.belongsTo(id, userId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pageOf(history.messages(id, userId, offset, size + 1), page, size));
    }

    private static long offset(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page deve ser maior ou igual a 0 e size deve estar entre 1 e 100.");
        }
        return (long) page * size;
    }

    private static <T> Page<T> pageOf(List<T> items, int page, int size) {
        boolean hasNext = items.size() > size;
        return new Page<>(hasNext ? items.subList(0, size) : items, page, size, hasNext);
    }

    public record Page<T>(List<T> items, int page, int size, boolean hasNext) {}
}
