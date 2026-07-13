package de.itsjxsper.advancedreports.backend.ratelimit.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {
    boolean serverUuid() default true;

    boolean playerUuid() default false;

    boolean discordUserId() default false;
}
