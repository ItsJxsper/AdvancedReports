package de.itsjxsper.advancedreports.backend.support;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.discord.data.entity.DiscordPlayerEntity;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import de.itsjxsper.advancedreports.common.model.player.PlayerUpdateDTO;
import de.itsjxsper.advancedreports.common.model.report.ReportCreateDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUpdateDto;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;

/**
 * Builders for entities and DTOs used across the test suite.
 * <p>
 * {@code ReportsEntity} in particular has six non-nullable columns and four mandatory associations,
 * so building one by hand in every test is both noisy and easy to get subtly wrong.
 */
public final class TestDataFactory {

    private TestDataFactory() {
    }

    // --- Entities ---

    public static PlayerEntity player(String name) {
        return player(UUID.randomUUID(), name);
    }

    public static PlayerEntity player(UUID uuid, String name) {
        PlayerEntity player = new PlayerEntity();
        player.setPlayerUuid(uuid);
        player.setPlayerName(name);
        return player;
    }

    public static CategoryEntity category(String name) {
        CategoryEntity category = new CategoryEntity();
        category.setName(name);
        category.setDisplayName(name.substring(0, 1).toUpperCase() + name.substring(1));
        category.setDescription("Description for " + name);
        category.setCooldownSec(60L);
        return category;
    }

    /**
     * {@code ServerEntity#serverUuid} is an assigned identifier — a Minecraft server registers under
     * its own configured UUID and there is no {@code @GeneratedValue} to fall back on. Leaving it
     * unset makes every {@code persist} fail with {@code IdentifierGenerationException}, so the
     * factory hands out one itself.
     */
    public static ServerEntity server() {
        return server(UUID.randomUUID());
    }

    public static ServerEntity server(UUID serverUuid) {
        ServerEntity server = new ServerEntity();
        server.setServerUuid(serverUuid);
        server.setIpAddress(loopback());
        server.setPort(25565);
        return server;
    }

    public static ScreenshotEntity screenshot(String objectKey) {
        ScreenshotEntity screenshot = new ScreenshotEntity();
        screenshot.setS3ObjectKey(objectKey);
        screenshot.setOriginalFilename("screenshot.png");
        screenshot.setContentType("image/png");
        screenshot.setFileSizeBytes(1024L);
        screenshot.setUploadStatus(UploadStatus.SUCCESS);
        return screenshot;
    }

    public static DiscordPlayerEntity discordPlayer(PlayerEntity player, Long discordUserId) {
        DiscordPlayerEntity discordPlayer = new DiscordPlayerEntity();
        discordPlayer.setPlayerEntity(player);
        discordPlayer.setDiscordUserId(discordUserId);
        return discordPlayer;
    }

    /**
     * A fully populated report. Every non-nullable association has to be a persisted entity, so the
     * caller passes them in rather than having them invented here.
     */
    public static ReportsEntity report(PlayerEntity reporter,
                                       PlayerEntity reported,
                                       PlayerEntity handledBy,
                                       CategoryEntity category,
                                       ServerEntity server) {
        ReportsEntity report = new ReportsEntity();
        report.setReporter(reporter);
        report.setReported(reported);
        report.setHandledBy(handledBy);
        report.setCategoryEntity(category);
        report.setServer(server);
        report.setReason("Suspected of flying");
        report.setLocation("world:100:64:-200");
        report.setReportStatus(ReportStatus.PENDING);
        report.setHandlerNote(null);
        return report;
    }

    // --- DTOs ---

    public static PlayerUpdateDTO playerUpdateDto(UUID uuid, String name) {
        return new PlayerUpdateDTO(uuid, Optional.ofNullable(name));
    }

    public static CategoryDto categoryDto(String name) {
        return new CategoryDto(null, name, "Display " + name, "Description", 60L, true);
    }

    public static ServerDto serverDto(UUID serverUuid) {
        return new ServerDto(serverUuid, loopback(), 25565);
    }

    public static ReportCreateDto reportCreateDto(UUID reporter,
                                                  UUID reported,
                                                  Long categoryId,
                                                  UUID serverUuid,
                                                  UUID handledBy) {
        return new ReportCreateDto(
                reporter,
                reported,
                categoryId,
                "Suspected of flying",
                serverUuid,
                "world:100:64:-200",
                ReportStatus.PENDING,
                handledBy,
                null,
                null
        );
    }

    public static ReportUpdateDto reportUpdateDto(UUID reporter,
                                                  UUID reported,
                                                  Long categoryId,
                                                  UUID serverUuid,
                                                  UUID handledBy) {
        return new ReportUpdateDto(
                reporter,
                reported,
                categoryId,
                "Suspected of flying",
                serverUuid,
                "world:100:64:-200",
                ReportStatus.PENDING,
                handledBy,
                null,
                null
        );
    }

    public static ScreenshotUpdateDto screenshotUpdateDto(String objectKey) {
        return new ScreenshotUpdateDto(
                "https://example.invalid/" + objectKey,
                objectKey,
                "screenshot.png",
                "image/png",
                1024L,
                UploadStatus.SUCCESS
        );
    }

    public static InetAddress loopback() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new IllegalStateException("127.0.0.1 could not be resolved", e);
        }
    }
}
