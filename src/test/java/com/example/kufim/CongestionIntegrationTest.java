package com.example.kufim;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.repository.FestivalRepository;
import com.example.kufim.stage.entity.Stage;
import com.example.kufim.stage.repository.StageRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 무대 구역 혼잡도: 권한, 입력 검증, append-only 이력, 공개 노출 범위, CSV. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CongestionIntegrationTest {
    @LocalServerPort int port;
    @Autowired FestivalRepository festivals;
    @Autowired StageRepository stages;
    @Autowired AdminAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();
    private long stageId;
    private Session superAdmin;
    private Session boothAdmin;

    record Session(List<String> cookies, String csrf) {}

    @BeforeAll
    void fixture() throws Exception {
        accounts.saveAndFlush(AdminAccount.builder().loginId("congestion-super").passwordHash(encoder.encode("pw-123456"))
                .name("혼잡도 관리자").role(AdminRole.SUPER_ADMIN).build());
        accounts.saveAndFlush(AdminAccount.builder().loginId("congestion-booth").passwordHash(encoder.encode("pw-123456"))
                .name("부스 운영자").role(AdminRole.BOOTH_ADMIN).build());
        var festival = festivals.saveAndFlush(Festival.builder().name("혼잡도 축제")
                .startAt(LocalDateTime.of(2026, 10, 1, 9, 0)).endAt(LocalDateTime.of(2026, 12, 31, 23, 0)).build());
        stageId = stages.saveAndFlush(Stage.builder().festival(festival).name("혼잡도 무대").displayOrder(1).build()).getId();
        superAdmin = login("congestion-super");
        boothAdmin = login("congestion-booth");
    }

    @Test
    void superAdminRecordsCongestionAndPublicSeesOnlyTheLatestWithoutRecorder() throws Exception {
        long zoneId = data(send("POST", "/api/v1/admin/stages/" + stageId + "/zones", "{\"name\":\"스탠딩 A구역\",\"displayOrder\":1}", superAdmin), 201)
                .path("zoneId").asLong();

        var first = data(send("POST", "/api/v1/admin/zones/" + zoneId + "/congestion", "{\"level\":\"NORMAL\",\"estimatedPeople\":300}", superAdmin), 201);
        assertThat(first.path("level").asString()).isEqualTo("NORMAL");
        assertThat(first.path("recordedBy").asString()).isEqualTo("혼잡도 관리자");
        // 같은 값 재입력도 허용(STG-002), 인원은 선택 입력(STG-003)
        data(send("POST", "/api/v1/admin/zones/" + zoneId + "/congestion", "{\"level\":\"VERY_CROWDED\"}", superAdmin), 201);

        var publicStages = send("GET", "/api/v1/stages", null, null);
        assertThat(publicStages.statusCode()).isEqualTo(200);
        assertThat(publicStages.body()).doesNotContain("혼잡도 관리자", "recordedBy");
        JsonNode stage = null;
        for (var s : json.readTree(publicStages.body()).path("data")) if (s.path("stageId").asLong() == stageId) stage = s;
        assertThat(stage).isNotNull();
        assertThat(stage.path("serverTime").isMissingNode()).isFalse();
        var zone = stage.path("zones").get(0);
        assertThat(zone.path("name").asString()).isEqualTo("스탠딩 A구역");
        assertThat(zone.path("level").asString()).isEqualTo("VERY_CROWDED");
        assertThat(zone.path("estimatedPeople").isNull()).isTrue();
        assertThat(zone.path("updatedAt").isNull()).isFalse();

        var history = data(send("GET", "/api/v1/stages/" + stageId + "/congestion/history", null, null), 200);
        assertThat(history.path("points").size()).isEqualTo(2);
        assertThat(history.path("points").get(0).path("estimatedPeople").asInt()).isEqualTo(300);

        // 이력이 있으면 삭제 불가, 비활성화하면 공개 목록에서 사라지고 입력도 막힘
        assertThat(error(send("DELETE", "/api/v1/admin/zones/" + zoneId, null, superAdmin), 409)).isEqualTo("Z002");
        data(send("PATCH", "/api/v1/admin/zones/" + zoneId, "{\"active\":false}", superAdmin), 200);
        assertThat(send("GET", "/api/v1/stages", null, null).body()).doesNotContain("스탠딩 A구역");
        assertThat(error(send("POST", "/api/v1/admin/zones/" + zoneId + "/congestion", "{\"level\":\"SMOOTH\"}", superAdmin), 409)).isEqualTo("Z003");

        var csv = client.send(request("GET", "/api/v1/admin/analytics/congestion.csv", null, superAdmin), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        var text = new String(csv.body(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿recorded_at,stage,zone,level,estimated_people,recorded_by");
        assertThat(text).contains("\"스탠딩 A구역\",NORMAL,300,\"혼잡도 관리자\"", "VERY_CROWDED");
    }

    @Test
    void inputIsValidatedAndZonesAreLimitedAndOnlySuperAdminCanWrite() throws Exception {
        var path = "/api/v1/admin/stages/" + stageId + "/zones";
        long zoneId = data(send("POST", path, "{\"name\":\"입장 대기열\"}", superAdmin), 201).path("zoneId").asLong();
        String record = "/api/v1/admin/zones/" + zoneId + "/congestion";

        assertThat(error(send("POST", record, "{\"level\":\"CROWDED\",\"estimatedPeople\":10000}", superAdmin), 400)).isEqualTo("C001");
        assertThat(error(send("POST", record, "{\"level\":\"CROWDED\",\"estimatedPeople\":-1}", superAdmin), 400)).isEqualTo("C001");
        assertThat(error(send("POST", record, "{\"estimatedPeople\":10}", superAdmin), 400)).isEqualTo("C001");
        assertThat(error(send("POST", path, "{\"name\":\"  \"}", superAdmin), 400)).isEqualTo("C001");

        assertThat(send("POST", record, "{\"level\":\"SMOOTH\"}", boothAdmin).statusCode()).isEqualTo(403);
        // 세션 없는 쓰기는 CSRF 단계에서 403, 세션 없는 조회는 401
        assertThat(send("POST", record, "{\"level\":\"SMOOTH\"}", null).statusCode()).isEqualTo(403);
        assertThat(send("GET", path, null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/admin/analytics/congestion.csv", null, boothAdmin).statusCode()).isEqualTo(403);

        // 이력이 없으면 삭제할 수 있다
        long empty = data(send("POST", path, "{\"name\":\"임시 구역\"}", superAdmin), 201).path("zoneId").asLong();
        assertThat(send("DELETE", "/api/v1/admin/zones/" + empty, null, superAdmin).statusCode()).isEqualTo(204);

        long active = data(send("GET", path, null, superAdmin), 200).path("zones").valueStream()
                .filter(z -> z.path("active").asBoolean()).count();
        for (long i = active; i < 10; i++) data(send("POST", path, "{\"name\":\"구역" + i + "\"}", superAdmin), 201);
        assertThat(error(send("POST", path, "{\"name\":\"11번째\"}", superAdmin), 409)).isEqualTo("Z001");
        assertThat(error(send("GET", "/api/v1/stages/" + stageId + "/congestion/history?from=2026-10-01T00:00:00%2B09:00&to=2026-10-03T00:00:00%2B09:00", null, null), 400)).isEqualTo("C001");
    }

    private Session login(String loginId) throws Exception {
        var res = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/admin/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"" + loginId + "\",\"password\":\"pw-123456\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        var cookies = res.headers().allValues("Set-Cookie").stream().map(c -> c.split(";", 2)[0]).toList();
        var csrf = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.substring(11)).findFirst().orElseThrow();
        return new Session(cookies, csrf);
    }
    private HttpRequest request(String method, String path, String body, Session session) {
        var b = HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (session != null) b.header("Cookie", String.join("; ", session.cookies())).header("X-XSRF-TOKEN", session.csrf());
        return b.build();
    }
    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        return client.send(request(method, path, body, session), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> res, int status) {
        assertThat(res.statusCode()).as(res.body()).isEqualTo(status);
        return json.readTree(res.body()).path("data");
    }
    private String error(HttpResponse<String> res, int status) {
        assertThat(res.statusCode()).as(res.body()).isEqualTo(status);
        return json.readTree(res.body()).path("error").path("code").asString();
    }
    private String url(String path) { return "http://localhost:" + port + path; }
}
