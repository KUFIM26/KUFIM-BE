package com.example.kufim;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminBooth;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.admin.repository.AdminBoothRepository;
import com.example.kufim.booth.entity.Booth;
import com.example.kufim.booth.repository.BoothRepository;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.repository.FestivalRepository;
import com.example.kufim.waiting.entity.Waiting;
import com.example.kufim.waiting.repository.WaitingRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 HTTP 요청으로 Swagger·권한·DTO와 Flyway/JPA 연결을 함께 검증합니다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiFoundationIntegrationTest {
    @LocalServerPort int port;
    @Autowired FestivalRepository festivals;
    @Autowired BoothRepository booths;
    @Autowired WaitingRepository waitings;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminBoothRepository assignments;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private Booth activeBooth;
    private Booth deletedBooth;
    private AdminAccount superAdmin;

    @BeforeAll
    void fixture() {
        superAdmin = accounts.saveAndFlush(AdminAccount.builder().loginId("test-super")
                .passwordHash(encoder.encode("test-password"))
                .name("테스트 관리자").role(AdminRole.SUPER_ADMIN).build());
        accounts.saveAndFlush(AdminAccount.builder().loginId("test-booth")
                .passwordHash(encoder.encode("test-password"))
                .name("테스트 운영자").role(AdminRole.BOOTH_ADMIN).build());
        var festival = festivals.saveAndFlush(Festival.builder().name("테스트 축제")
                .startAt(LocalDateTime.of(2026, 9, 30, 9, 0))
                .endAt(LocalDateTime.of(2026, 10, 2, 23, 0)).build());
        activeBooth = booths.saveAndFlush(Booth.builder().festival(festival)
                .name("테스트 부스").place("청심대").boothCode("SECRETQR1234").build());
        deletedBooth = booths.saveAndFlush(Booth.builder().festival(festival)
                .name("삭제된 부스").place("청심대").deleted(true).build());
        waitings.saveAndFlush(Waiting.builder().booth(activeBooth)
                .clientToken(UUID.randomUUID().toString()).studentNumber("202300012")
                .phoneNumber("01012341234").waitingNumber(1).partySize((byte) 3)
                .registeredAt(LocalDateTime.now()).build());
    }

    @Test
    void swaggerAndOpenApiAreAvailableWithoutAuthentication() throws Exception {
        var swagger = get("/swagger-ui.html", null);
        assertThat(swagger.statusCode()).isEqualTo(200);
        assertThat(swagger.body()).contains("swagger-ui");
        var api = get("/v3/api-docs", null);
        assertThat(api.statusCode()).isEqualTo(200);
        assertThat(api.body()).contains("/api/v1/booths", "/api/v1/admin/waitings", "partySize", "cookieAuth");
        var publicGroup = get("/v3/api-docs/01-public", null);
        assertThat(publicGroup.body()).contains("/api/v1/festival").doesNotContain("/api/v1/admin/");
        assertThat(get("/v3/api-docs/00-all", null).body()).contains("/api/v1/festival", "/api/v1/admin/festivals", "/api/v1/admin/map/floorplans");
        assertThat(get("/v3/api-docs/02-admin", null).body()).contains("/api/v1/admin/accounts", "/api/v1/admin/festivals");
    }

    @Test
    void superAdminCanCreateFestivalForBoothAndStageRegistration() throws Exception {
        var login = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"test-super\",\"password\":\"test-password\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        var cookies = login.headers().allValues("Set-Cookie").stream().map(c -> c.split(";", 2)[0]).toList();
        var csrf = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring("XSRF-TOKEN=".length())).findFirst().orElseThrow();
        var created = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/festivals"))
                .header("Content-Type", "application/json")
                .header("Cookie", String.join("; ", cookies))
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"등록용 축제\",\"location\":\"건국대학교\","
                        + "\"startAt\":\"2026-10-08T09:00:00+09:00\",\"endAt\":\"2026-10-09T23:00:00+09:00\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("등록용 축제", "festivalId", "건국대학교");
        assertThat(get("/api/v1/admin/festivals", "test-super").body()).contains("등록용 축제");
        long festivalId = json.readTree(created.body()).path("data").path("festivalId").asLong();
        var floorplan = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/map/floorplans"))
                .header("Content-Type", "application/json")
                .header("Cookie", String.join("; ", cookies))
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"festivalId\":" + festivalId
                        + ",\"imageUrl\":\"/map.png\",\"width\":1000,\"height\":800}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(floorplan.statusCode()).isEqualTo(201);
        var booth = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/booths"))
                .header("Content-Type", "application/json")
                .header("Cookie", String.join("; ", cookies))
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"festivalId\":" + festivalId
                        + ",\"name\":\"지도 부스\",\"organizerType\":\"CLUB\",\"period\":\"DAY\","
                        + "\"place\":\"광장\",\"pin\":{\"xPercent\":50,\"yPercent\":40}}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(booth.statusCode()).isEqualTo(201);
        assertThat(booth.body()).contains("지도 부스", "xPercent");
    }

    @Test
    void publicReadResponseDoesNotExposeQrSecretsOrDeletedRows() throws Exception {
        var response = get("/api/v1/booths", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("테스트 부스", "boothId", "\"success\":true")
                .doesNotContain("삭제된 부스", "SECRETQR1234", "boothCode", "waitingSeq");
        assertThat(get("/api/v1/booths/" + deletedBooth.getId(), null).statusCode()).isEqualTo(404);
    }

    @Test
    void adminApisRequireSessionAndRole() throws Exception {
        String path = "/api/v1/admin/booths/" + activeBooth.getId() + "/waitings";
        assertThat(get(path, null).statusCode()).isEqualTo(401);
        assertThat(get(path, "test-booth").statusCode()).isEqualTo(403);
        assertThat(get(path, "test-super").statusCode()).isEqualTo(200);
    }

    @Test
    void adminDtoMasksPersonalDataAndHidesCredentials() throws Exception {
        var waiting = get("/api/v1/admin/booths/" + activeBooth.getId() + "/waitings", "test-super");
        assertThat(waiting.body()).contains("2023*****", "010****1234", "partySize")
                .doesNotContain("202300012", "01012341234", "clientToken");
        assertThat(get("/api/v1/admin/accounts", "test-super").body())
                .doesNotContain("passwordHash", "test-password", "$2a$");
    }

    @Test
    void invalidPaginationAndMissingRecordsReturnConsistentErrors() throws Exception {
        var invalid = get("/api/v1/notifications?size=101", null);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("C001");
        assertThat(get("/api/v1/notifications?page=-1", null).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/booths/not-a-number", null).statusCode()).isEqualTo(400);
        var missing = get("/api/v1/booths/999999", null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("B001");
    }

    @Test
    void migrationContainsAllActiveTablesAndNoDeferredTables() {
        var names = jdbc.queryForList("select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(names).containsAll(List.of("festival", "admin_account", "admin_booth", "stage",
                "performance", "booth", "facility", "map_floorplan", "map_pin", "notice", "waiting",
                "waiting_event", "sms_send_log", "audit_log", "privacy_purge_log", "notification", "notification_read"))
                .doesNotContain("translation_cache", "stage_zone", "congestion_log", "booth_image", "map_calibration_point");
        assertThat(activeBooth.getCreatedAt()).isNotNull();
        assertThat(activeBooth.getUpdatedAt()).isNotNull();
    }

    @Test
    void databaseRejectsDuplicateAdminBoothAssignment() {
        assignments.saveAndFlush(AdminBooth.builder().adminAccount(superAdmin).booth(activeBooth).build());
        assertThatThrownBy(() -> assignments.saveAndFlush(
                AdminBooth.builder().adminAccount(superAdmin).booth(activeBooth).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusEndpointConfirmsApplicationIsRunning() throws Exception {
        var response = get("/api/v1/status", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("KUFIM-BE", "UP");
    }

    private HttpResponse<String> get(String path, String login) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (login != null) {
            var auth = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"" + login + "\",\"password\":\"test-password\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(auth.statusCode()).isEqualTo(200);
            var cookies = auth.headers().allValues("Set-Cookie").stream().map(c -> c.split(";", 2)[0]).toList();
            request.header("Cookie", String.join("; ", cookies));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
