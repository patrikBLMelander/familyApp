package com.familyapp.api.admin;

import com.familyapp.application.admin.AdminStatsService;
import com.familyapp.application.admin.AdminStatsService.AdminStats;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The admin statistics page's numbers: sums and shares over the whole user base. */
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService statsService;
    private final AdminAccess adminAccess;

    public AdminStatsController(AdminStatsService statsService, AdminAccess adminAccess) {
        this.statsService = statsService;
        this.adminAccess = adminAccess;
    }

    /** Statistics for the last {@code days} days: 7, 30 (default) or 90. */
    @GetMapping
    public AdminStats stats(
            @RequestParam(value = "days", defaultValue = "30") int days,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        adminAccess.requireAdmin(deviceToken);
        if (!AdminStatsService.PERIODS.contains(days)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be one of " + AdminStatsService.PERIODS);
        }
        return statsService.stats(days);
    }
}
