package com.example.kufim;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.booth.entity.Booth;
import com.example.kufim.booth.repository.BoothRepository;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.repository.FestivalRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

/** 축제 수정, 도면 교체, 본인 비밀번호 변경, 부스 운영자 계정 발급 흐름. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminManagementIntegrationTest {
    @LocalServerPort int port;
    @Autowired FestivalRepository festivals;
    @Autowired BoothRepository booths;
    @Autowired AdminAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();
    private long festivalId;
    private long boothId;

    record Session(List<String> cookies, String csrf) {}

    @BeforeAll
    void fixture() {
        accounts.saveAndFlush(AdminAccount.builder().loginId("mgmt-super").passwordHash(encoder.encode("super-password-1"))
                .name("운영 총괄").role(AdminRole.SUPER_ADMIN).build());
        accounts.saveAndFlush(AdminAccount.builder().loginId("mgmt-self").passwordHash(encoder.encode("old-password-123"))
                .name("비밀번호 변경자").role(AdminRole.SUPER_ADMIN).build());
        var festival = festivals.saveAndFlush(Festival.builder().name("수정 전 축제")
                .startAt(LocalDateTime.of(2026, 10, 28, 9, 0)).endAt(LocalDateTime.of(2026, 10, 30, 23, 59)).build());
        festivalId = festival.getId();
        boothId = booths.saveAndFlush(Booth.builder().festival(festival).name("배정 부스").place("청심대").waitingEnabled(true).build()).getId();
    }

    @Test
    void festivalAndFloorplanCanBeCorrectedAfterRegistration() throws Exception {
        var admin = login("mgmt-super", "super-password-1");
        var edited = data(send("PATCH", "/api/v1/admin/festivals/" + festivalId,
                "{\"name\":\"2026 성신의 예술제\",\"endAt\":\"2026-10-31T23:00:00+09:00\"}", admin), 200);
        assertThat(edited.path("name").asString()).isEqualTo("2026 성신의 예술제");
        assertThat(edited.path("endAt").asString()).startsWith("2026-10-31T23:00");
        assertThat(edited.path("startAt").asString()).startsWith("2026-10-28T09:00");
        assertThat(error(send("PATCH", "/api/v1/admin/festivals/" + festivalId,
                "{\"endAt\":\"2026-10-01T00:00:00+09:00\"}", admin), 400)).isEqualTo("C001");

        long floorplanId = data(send("POST", "/api/v1/admin/map/floorplans",
                "{\"festivalId\":" + festivalId + ",\"imageUrl\":\"/old.png\",\"width\":800,\"height\":800}", admin), 201)
                .path("floorplanId").asLong();
        data(send("PATCH", "/api/v1/admin/booths/" + boothId, "{\"pin\":{\"xPercent\":30,\"yPercent\":40}}", admin), 200);
        var replaced = data(send("PATCH", "/api/v1/admin/map/floorplans/" + floorplanId,
                "{\"imageUrl\":\"/campus.png\",\"width\":1600,\"height\":1200}", admin), 200);
        assertThat(replaced.path("imageUrl").asString()).isEqualTo("/campus.png");
        var map = data(send("GET", "/api/v1/map", null, null), 200).path("floorplan");
        assertThat(map.path("width").asInt()).isEqualTo(1600);
        var pins = data(send("GET", "/api/v1/map/pins", null, null), 200);
        assertThat(pins.toString()).contains("배정 부스", "30");
    }

    @Test
    void ownPasswordChangeKeepsThisSessionAndEndsOthers() throws Exception {
        var phone = login("mgmt-self", "old-password-123");
        var laptop = login("mgmt-self", "old-password-123");
        assertThat(error(send("POST", "/api/v1/admin/auth/password",
                "{\"currentPassword\":\"wrong-password\",\"newPassword\":\"new-password-456\"}", laptop), 400)).isEqualTo("A001");
        assertThat(error(send("POST", "/api/v1/admin/auth/password",
                "{\"currentPassword\":\"old-password-123\",\"newPassword\":\"short\"}", laptop), 400)).isEqualTo("C001");
        assertThat(send("POST", "/api/v1/admin/auth/password",
                "{\"currentPassword\":\"old-password-123\",\"newPassword\":\"new-password-456\"}", laptop).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/v1/admin/auth/me", null, laptop).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/admin/auth/me", null, phone).statusCode()).isEqualTo(401);
        assertThat(loginStatus("mgmt-self", "old-password-123")).isEqualTo(401);
        assertThat(loginStatus("mgmt-self", "new-password-456")).isEqualTo(200);
    }

    @Test
    void boothOperatorAccountIsIssuedAndSeesOnlyItsBooth() throws Exception {
        var admin = login("mgmt-super", "super-password-1");
        long id = data(send("POST", "/api/v1/admin/accounts",
                "{\"loginId\":\"booth-op-1\",\"name\":\"컴공 운영자\",\"role\":\"BOOTH_ADMIN\",\"managedBoothIds\":[" + boothId + "]}", admin), 201)
                .path("accountId").asLong();
        // 새 계정은 비활성·무작위 비밀번호로 만들어지므로 비밀번호 설정과 활성화가 필요하다
        assertThat(loginStatus("booth-op-1", "booth-op-password")).isEqualTo(401);
        assertThat(send("POST", "/api/v1/admin/accounts/" + id + "/reset-password",
                "{\"newPassword\":\"booth-op-password\"}", admin).statusCode()).isEqualTo(204);
        data(send("PATCH", "/api/v1/admin/accounts/" + id, "{\"active\":true}", admin), 200);
        var operator = login("booth-op-1", "booth-op-password");
        var me = data(send("GET", "/api/v1/admin/auth/me", null, operator), 200);
        assertThat(me.path("role").asString()).isEqualTo("BOOTH_ADMIN");
        assertThat(me.path("managedBoothIds").get(0).asLong()).isEqualTo(boothId);
        assertThat(send("GET", "/api/v1/admin/booths/" + boothId + "/waitings", null, operator).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/admin/accounts", null, operator).statusCode()).isEqualTo(403);
    }

    private Session login(String loginId, String password) throws Exception {
        var res = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/admin/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        var cookies = res.headers().allValues("Set-Cookie").stream().map(c -> c.split(";", 2)[0]).toList();
        var csrf = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.substring(11)).findFirst().orElseThrow();
        return new Session(cookies, csrf);
    }
    private int loginStatus(String loginId, String password) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/admin/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }
    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (session != null) b.header("Cookie", String.join("; ", session.cookies())).header("X-XSRF-TOKEN", session.csrf());
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
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
