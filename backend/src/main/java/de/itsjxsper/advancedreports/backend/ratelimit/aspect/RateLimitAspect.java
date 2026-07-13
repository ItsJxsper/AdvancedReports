package de.itsjxsper.advancedreports.backend.ratelimit.aspect;

import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.backend.ratelimit.exceptions.MissingHeaderException;
import de.itsjxsper.advancedreports.backend.ratelimit.exceptions.RateLimitExceededException;
import de.itsjxsper.advancedreports.backend.ratelimit.service.RateLimiterService;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    public static final String HEADER_SERVER_UUID = "X-Server-UUID";
    public static final String HEADER_PLAYER_UUID = "X-Player-UUID";
    public static final String HEADER_DISCORD_ID = "X-Discord-ID";

    private final RateLimiterService rateLimiterService;

    @Around("@annotation(rateLimited)")
    public Object handleRateLimit(ProceedingJoinPoint pjp, RateLimited rateLimited) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();
        HttpServletResponse response = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getResponse();

        // --- Server UUID ---
        if (rateLimited.serverUuid()) {
            String serverUuid = request.getHeader(HEADER_SERVER_UUID);

            if (serverUuid == null || serverUuid.isBlank()) {
                throw new MissingHeaderException(HEADER_SERVER_UUID);
            }

            ConsumptionProbe probe = rateLimiterService.tryConsumeServer(serverUuid);
            setRemainingHeader(response, "X-RateLimit-Server-Remaining", probe);

            if (!probe.isConsumed()) {
                throw new RateLimitExceededException(serverUuid, probe.getNanosToWaitForRefill());
            }
        }

        // --- Player UUID ---
        if (rateLimited.playerUuid()) {
            String playerUuid = request.getHeader(HEADER_PLAYER_UUID);

            if (playerUuid == null || playerUuid.isBlank()) {
                throw new MissingHeaderException(HEADER_PLAYER_UUID);
            }

            ConsumptionProbe probe = rateLimiterService.tryConsumePlayer(playerUuid);
            setRemainingHeader(response, "X-RateLimit-Player-Remaining", probe);

            if (!probe.isConsumed()) {
                throw new RateLimitExceededException(playerUuid, probe.getNanosToWaitForRefill());
            }
        }

        if (rateLimited.discordUserId()) {
            String discordId = request.getHeader(HEADER_DISCORD_ID);

            if (discordId == null || discordId.isBlank()) {
                throw new MissingHeaderException(HEADER_DISCORD_ID);
            }

            ConsumptionProbe probe = rateLimiterService.tryConsumeDiscord(discordId);
            setRemainingHeader(response, "X-RateLimit-Discord-Remaining", probe);

            if (!probe.isConsumed()) {
                throw new RateLimitExceededException(discordId, probe.getNanosToWaitForRefill());
            }
        }

        return pjp.proceed();
    }

    private void setRemainingHeader(HttpServletResponse response, String header, ConsumptionProbe probe) {
        if (response != null) {
            response.setHeader(header, String.valueOf(probe.getRemainingTokens()));
        }
    }
}
