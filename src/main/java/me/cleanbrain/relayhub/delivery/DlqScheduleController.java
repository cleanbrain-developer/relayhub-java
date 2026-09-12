package me.cleanbrain.relayhub.delivery;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.delivery.dto.DlqScheduleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.temporal.ChronoUnit;

/** Read-only, like MetricsController — lets the Live page show a literal countdown to
 *  DlqAutoReplayScheduler's next sweep instead of only reacting after the fact. */
@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DlqScheduleController {

    private final DlqAutoReplayScheduler scheduler;

    @GetMapping("/schedule")
    public DlqScheduleResponse schedule() {
        return new DlqScheduleResponse(
                scheduler.getIntervalMs(),
                scheduler.getLastRunAt(),
                scheduler.getLastRunAt().plus(scheduler.getIntervalMs(), ChronoUnit.MILLIS));
    }
}
