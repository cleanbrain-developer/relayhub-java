package me.cleanbrain.relayhub.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.event.dto.EventResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found: " + id));
        return EventResponse.from(event, objectMapper);
    }

    @GetMapping
    public List<EventResponse> listBySourceEvent(@RequestParam UUID sourceEventId) {
        return eventRepository.findBySourceEventId(sourceEventId).stream()
                .map(event -> EventResponse.from(event, objectMapper))
                .toList();
    }
}
