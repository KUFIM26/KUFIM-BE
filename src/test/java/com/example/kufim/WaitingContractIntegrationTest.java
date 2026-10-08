package com.example.kufim;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.entity.AdminBooth;
import com.example.kufim.admin.repository.AdminBoothRepository;
import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.booth.entity.Booth;
import com.example.kufim.booth.repository.BoothRepository;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.repository.FestivalRepository;
import com.example.kufim.map.entity.MapFloorplan;
import com.example.kufim.map.repository.MapFloorplanRepository;
import com.example.kufim.waiting.service.WaitingWorkflowService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WaitingContractIntegrationTest {
    @LocalServerPort int port;
    @Autowired FestivalRepository festivals;
    @Autowired BoothRepository booths;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminBoothRepository assignments;
    @Autowired WaitingWorkflowService workflow;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired MapFloorplanRepository floorplans;
    private final HttpClient client = HttpClient.newHttpClient();
    private long first;
    private long second;
    private long festivalId;
    private AdminAccount admin;

    @BeforeEach
    void fixture() {
        var festival = festivals.saveAndFlush(Festival.builder().name("계약 검증 축제")
                .startAt(LocalDateTime.now().minusDays(1)).endAt(LocalDateTime.now().plusDays(1)).build());
        festivalId = festival.getId();
        first = booths.saveAndFlush(Booth.builder().festival(festival).name("첫 부스")
                .place("광장").waitingEnabled(true).build()).getId();
        second = booths.saveAndFlush(Booth.builder().festival(festival).name("둘째 부스")
                .place("광장").waitingEnabled(true).build()).getId();
        admin = accounts.saveAndFlush(AdminAccount.builder().loginId("admin-" + UUID.randomUUID())
                .passwordHash(encoder.encode("secret")).name("운영자").role(AdminRole.SUPER_ADMIN).build());
    }

    @Test
    void oneActiveWaitingAcrossBoothsAndBrowserTokenRestore() throws Exception {
        String token = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        var created = post(first, token, "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":4,\"privacyConsent\":true}");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"success\":true", "\"partySize\":4", "\"status\":\"WAITING\"");
        var id = waitingId(created.body());
        assertThat(post(second, token, "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":4,\"privacyConsent\":true}").body())
                .contains("\"code\":\"W001\"", id);
        assertThat(post(second, other, "{\"studentNumber\":\"202300013\",\"phoneNumber\":\"01012345679\",\"partySize\":2,\"privacyConsent\":true}").statusCode())
                .isEqualTo(201);
        assertThat(get("/api/v1/waitings/me", token).body()).contains(id).doesNotContain("01012345678", "202300012");
        assertThat(get("/api/v1/waitings/" + id, other).statusCode()).isEqualTo(403);
    }

    @Test
    void validationRecallAndOriginalTenMinuteDeadline() throws Exception {
        String token = UUID.randomUUID().toString();
        assertThat(post(first, token, "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":7,\"privacyConsent\":true}").statusCode())
                .isEqualTo(400);
        assertThat(post(first, token, "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":1,\"privacyConsent\":false}").body())
                .contains("\"code\":\"C002\"");
        var created = post(first, token, "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":6,\"privacyConsent\":true}");
        var id = waitingId(created.body());
        var firstCall = workflow.call(id, admin, "first-call");
        var deadline = firstCall.get("autoCancelAt");
        assertThat(workflow.call(id, admin, "first-call").get("recallCount")).isEqualTo(0);
        var recall = workflow.call(id, admin, "second-call");
        assertThat(recall.get("autoCancelAt")).isEqualTo(deadline);
        assertThat(recall.get("recallCount")).isEqualTo(1);
        jdbc.update("update waiting set auto_cancel_at=? where public_id=?", LocalDateTime.now().minusMinutes(1), id);
        assertThat(workflow.autoCancel(id)).isTrue();
        assertThat(get("/api/v1/waitings/me", token).body()).contains("\"data\":[]");
        assertThat(get("/api/v1/waitings/" + id, token).body()).contains("AUTO_CANCELED");
        assertThat(post(second, token, "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":1,\"privacyConsent\":true}").statusCode())
                .isEqualTo(201);
    }

    @Test
    void simultaneousRegistrationHasOneWinnerAndIdempotencyReturnsFirstTicket() throws Exception {
        String token = UUID.randomUUID().toString();
        String body = "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":2,\"privacyConsent\":true}";
        var one = CompletableFuture.supplyAsync(() -> postUnchecked(first, token, body));
        var two = CompletableFuture.supplyAsync(() -> postUnchecked(second, token, body));
        var statuses = java.util.List.of(one.join().statusCode(), two.join().statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from active_waiting where client_token=?", Integer.class, token)).isEqualTo(1);
        String another = UUID.randomUUID().toString();
        var firstResponse = postWithKey(first, another, body, "retry-1");
        var repeated = postWithKey(first, another, body, "retry-1");
        assertThat(repeated.statusCode()).isEqualTo(201);
        assertThat(waitingId(repeated.body())).isEqualTo(waitingId(firstResponse.body()));
    }

    @Test
    void sessionCsrfBoothScopeAndPersonalNotificationRead() throws Exception {
        var boothAdmin = accounts.saveAndFlush(AdminAccount.builder().loginId("booth-" + UUID.randomUUID())
                .passwordHash(encoder.encode("secret")).name("부스 운영자").role(AdminRole.BOOTH_ADMIN).build());
        assignments.saveAndFlush(AdminBooth.builder().adminAccount(boothAdmin).booth(booths.findById(first).orElseThrow()).build());
        var auth = login(boothAdmin.getLoginId());
        assertThat(auth.statusCode()).isEqualTo(200);
        String cookie = auth.headers().allValues("Set-Cookie").stream().map(s -> s.split(";", 2)[0])
                .collect(java.util.stream.Collectors.joining("; "));
        assertThat(adminGet("/api/v1/admin/booths/" + first + "/waiting-config", cookie).statusCode()).isEqualTo(200);
        assertThat(adminGet("/api/v1/admin/booths/" + second + "/waiting-config", cookie).statusCode()).isEqualTo(403);
        String token = UUID.randomUUID().toString();
        String id = waitingId(post(first, token, "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":2,\"privacyConsent\":true}").body());
        var noCsrf = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/waitings/" + id + "/call"))
                .header("Cookie", cookie).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        var csrf = auth.headers().allValues("Set-Cookie").stream().map(s -> s.split(";", 2)[0])
                .filter(s -> s.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().substring("XSRF-TOKEN=".length());
        var call = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/waitings/" + id + "/call"))
                .header("Cookie", cookie).header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(call.statusCode()).isEqualTo(200);
        var list = get("/api/v1/notifications", token);
        assertThat(list.body()).contains("WAITING_CALL");
        var matcher = Pattern.compile("\\\"notificationId\\\":([0-9]+)").matcher(list.body());
        assertThat(matcher.find()).isTrue();
        var read = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/notifications/" + matcher.group(1) + "/read"))
                .header("X-Client-Token", token).method("PATCH", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(read.statusCode()).isEqualTo(200);
        var readAll = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/notifications/read-all"))
                .header("X-Client-Token", token).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{}")) .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(readAll.statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/notifications", token).body()).contains("\"unreadCount\":0");
    }

    @Test
    void contentNoticeAndQrWorkThroughAdminSession() throws Exception {
        var auth = login(admin.getLoginId());
        assertThat(auth.statusCode()).isEqualTo(200);
        String cookie = auth.headers().allValues("Set-Cookie").stream().map(s -> s.split(";", 2)[0])
                .collect(java.util.stream.Collectors.joining("; "));
        String csrf = auth.headers().allValues("Set-Cookie").stream().map(s -> s.split(";", 2)[0])
                .filter(s -> s.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().substring("XSRF-TOKEN=".length());
        var notice = adminPost("/api/v1/admin/notices", cookie, csrf,
                "{\"title\":\"운영 안내\",\"content\":\"입장을 확인하세요\",\"category\":\"INFO\"}");
        assertThat(notice.statusCode()).isEqualTo(201);
        assertThat(get("/api/v1/notices", UUID.randomUUID().toString()).body()).contains("운영 안내");
        var qr = adminPost("/api/v1/admin/booths/" + first + "/qr", cookie, csrf, "");
        assertThat(qr.statusCode()).isEqualTo(200);
        var matcher = Pattern.compile("\\\"boothCode\\\":\\\"([0-9a-f]{12})\\\"").matcher(qr.body());
        assertThat(matcher.find()).isTrue();
        assertThat(get("/api/v1/booths/code/" + matcher.group(1), UUID.randomUUID().toString()).body()).contains("첫 부스");
        var png = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/booths/" + first + "/qr"))
                .header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(png.statusCode()).isEqualTo(200);
        assertThat(png.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).contains("image/png"));
        assertThat(png.body()).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        var stage = adminPost("/api/v1/admin/stages", cookie, csrf,
                "{\"festivalId\":" + festivalId + ",\"name\":\"중앙무대\",\"displayOrder\":1}");
        assertThat(stage.statusCode()).isEqualTo(201);
        assertThat(get("/api/v1/stages", UUID.randomUUID().toString()).body()).contains("중앙무대");
    }

    @Test
    void pausedBlocksRegistrationButAllowsCallAndClosedBlocksFirstCall() throws Exception {
        String token = UUID.randomUUID().toString();
        String body = "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":2,\"privacyConsent\":true}";
        String id = waitingId(post(first, token, body).body());
        jdbc.update("update booth set accepting_status='PAUSED' where id=?", first);
        assertThat(post(first, UUID.randomUUID().toString(), body).body()).contains("\"code\":\"W002\"");
        assertThat(workflow.call(id, admin).get("status")).isEqualTo(com.example.kufim.waiting.entity.WaitingStatus.CALLED);
        jdbc.update("update booth set accepting_status='CLOSED' where id=?", first);
        assertThat(workflow.call(id, admin).get("recallCount")).isEqualTo(1);
        String another = waitingId(post(second, UUID.randomUUID().toString(), body).body());
        jdbc.update("update booth set accepting_status='CLOSED' where id=?", second);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflow.call(another, admin))
                .isInstanceOf(com.example.kufim.common.exception.ContractException.class);
    }

    @Test
    void adminContentPinPatchAndForceLogout() throws Exception {
        floorplans.saveAndFlush(MapFloorplan.builder().festival(festivals.findById(festivalId).orElseThrow())
                .imageUrl("https://example.org/map.png").width(1000).height(800).build());
        var auth = login(admin.getLoginId());
        String cookie = auth.headers().allValues("Set-Cookie").stream().map(s -> s.split(";", 2)[0])
                .collect(java.util.stream.Collectors.joining("; "));
        String csrf = auth.headers().allValues("Set-Cookie").stream().map(s -> s.split(";", 2)[0])
                .filter(s -> s.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().substring("XSRF-TOKEN=".length());
        var created = adminPost("/api/v1/admin/facilities", cookie, csrf,
                "{\"festivalId\":" + festivalId + ",\"type\":\"RESTROOM\",\"name\":\"화장실\",\"place\":\"본관\",\"pin\":{\"xPercent\":25.5,\"yPercent\":70.0}}");
        assertThat(created.statusCode()).isEqualTo(201);
        var matcher = Pattern.compile("\\\"facilityId\\\":([0-9]+)").matcher(created.body());
        assertThat(matcher.find()).isTrue();
        String id = matcher.group(1);
        assertThat(get("/api/v1/facilities/" + id, UUID.randomUUID().toString()).body()).contains("25.50", "70.00");
        var cleared = adminPatch("/api/v1/admin/facilities/" + id, cookie, csrf, "{\"pin\":null}");
        assertThat(cleared.statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/facilities/" + id, UUID.randomUUID().toString()).body()).contains("\"pin\":null");
        var forced = adminPost("/api/v1/admin/accounts/" + admin.getId() + "/force-logout", cookie, csrf, "");
        assertThat(forced.statusCode()).isEqualTo(204);
        assertThat(adminGet("/api/v1/admin/auth/me", cookie).statusCode()).isEqualTo(401);
    }

    @Test
    void privacyPurgeRemovesStoredStudentAndPhoneNumbers() throws Exception {
        String token = UUID.randomUUID().toString();
        String id = waitingId(post(first, token,
                "{\"studentNumber\":\"202300012\",\"phoneNumber\":\"01012345678\",\"partySize\":1,\"privacyConsent\":true}").body());
        workflow.adminClose(id, admin, com.example.kufim.waiting.entity.WaitingStatus.CANCELED);
        jdbc.update("update waiting set closed_at=? where public_id=?", LocalDateTime.now().minusDays(3), id);
        var auth = login(admin.getLoginId());
        String cookie = auth.headers().allValues("Set-Cookie").stream().map(s -> s.split(";", 2)[0])
                .collect(java.util.stream.Collectors.joining("; "));
        String csrf = auth.headers().allValues("Set-Cookie").stream().map(s -> s.split(";", 2)[0])
                .filter(s -> s.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().substring("XSRF-TOKEN=".length());
        var result = adminPost("/api/v1/admin/privacy/purge", cookie, csrf,
                "{\"beforeAt\":\"" + java.time.OffsetDateTime.now().minusDays(2).withNano(0) + "\",\"confirmation\":\"DELETE_PERSONAL_DATA\"}");
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select student_number from waiting where public_id=?", String.class, id)).isEqualTo("000000000");
        assertThat(jdbc.queryForObject("select phone_number from waiting where public_id=?", String.class, id)).isEqualTo("00000000000");
        assertThat(adminGet("/api/v1/admin/waitings/" + id + "/contact", cookie).body()).contains("\"code\":\"W010\"");
    }

    private HttpResponse<String> post(long boothId, String token, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/booths/" + boothId + "/waitings"))
                .header("Content-Type", "application/json").header("X-Client-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> postUnchecked(long boothId, String token, String body) {
        try { return post(boothId, token, body); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private HttpResponse<String> postWithKey(long boothId, String token, String body, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/booths/" + boothId + "/waitings"))
                .header("Content-Type", "application/json").header("X-Client-Token", token).header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> login(String login) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"" + login + "\",\"password\":\"secret\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> adminPost(String path, String cookie, String csrf, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", cookie).header("X-XSRF-TOKEN", csrf);
        if (!body.isEmpty()) builder.header("Content-Type", "application/json");
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> adminPatch(String path, String cookie, String csrf, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", cookie).header("X-XSRF-TOKEN", csrf).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> adminGet(String path, String cookie) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Client-Token", token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private String waitingId(String body) {
        var matcher = Pattern.compile("\\\"waitingId\\\":\\\"([0-9a-f-]{36})\\\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
