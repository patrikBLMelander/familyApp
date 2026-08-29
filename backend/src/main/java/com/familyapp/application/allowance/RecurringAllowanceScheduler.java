package com.familyapp.application.allowance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Kör den automatiska pengen en gång om dygnet.
 *
 * Egen böna, inte en metod i tjänsten, och det är inte kosmetik: ett anrop till en
 * @Transactional-metod på samma objekt går förbi Springs proxy, så REQUIRES_NEW hade
 * aldrig gällt och varje barns utbetalning hade delat transaktion med alla andra.
 * Det är just den gränsen som gör det unika indexet på (member_id, due_date) till ett
 * skydd mot dubbelbetalning i stället för en förhoppning.
 *
 * Klockan fyra svensk tid ligger efter XP-nollställningen, som går midnatt UTC den
 * 1:a. Nivån för månaden som gick hinner alltså hamna i historiken innan den läses --
 * och tjänsten klarar ändå båda ordningarna, eftersom en cron-ordning inte är något
 * att förlita sig på.
 */
@Component
public class RecurringAllowanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringAllowanceScheduler.class);

    private final RecurringAllowanceService service;

    public RecurringAllowanceScheduler(RecurringAllowanceService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Europe/Stockholm")
    public void payDueAllowances() {
        var today = LocalDate.now(RecurringAllowanceService.ZONE);
        var due = service.dueScheduleIds(today);
        if (due.isEmpty()) {
            return;
        }
        log.info("Automatisk utbetalning: {} scheman förfallna per {}", due.size(), today);
        for (var scheduleId : due) {
            try {
                service.payOne(scheduleId, today);
            } catch (Exception e) {
                // Ett barns schema får inte stoppa syskonens.
                log.error("Automatisk utbetalning misslyckades för schema {}: {}", scheduleId, e.getMessage(), e);
            }
        }
    }
}
