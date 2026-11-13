package com.example.kufim;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.repository.FestivalRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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

/**
 * 긴급 신고의 상태 전이와 알림 저장.
 *
 * 상태가 바뀔 때 REPORT_STATUS 알림이 함께 저장되는데, 이 INSERT가 notification의
 * CHECK 제약에 걸려 접수 자체가 실패한 적이 있습니다(V7 누락 → V8에서 수정).
 * 그 경로를 매번 지나가도록 접수·메모·완료를 모두 호출합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportContractIntegrationTest {
    @LocalServerPort int port;
    @Autowired FestivalRepository festivals;
    @Autowired AdminAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    record Session(List<String> cookies, String csrf) {}

    @BeforeAll
    void fixture() {
        accounts.saveAndFlush(AdminAccount.builder().loginId("report-super")
                .passwordHash(encoder.encode("super-password-1"))
                .name("상황실").role(AdminRole.SUPER_ADMIN).build());
        festivals.saveAndFlush(Festival.builder().name("신고 테스트 축제")
                .startAt(LocalDateTime.of(2026, 10, 28, 9, 0))
                .endAt(LocalDateTime.of(2026, 10, 30, 23, 59)).build());
    }

    @Test
    void statusMovesThroughReceiveMemoAndComplete() throws Exception {
        String token = UUID.randomUUID().toString();
        long reportId = register(token, "SAFETY", "계단에서 넘어졌어요");
        var admin = login();

        // 상세를 열면 접수로 넘어간다. 알림 저장이 실패하면 여기서 500이 난다.
        var received = data(send("POST", "/api/v1/admin/reports/" + reportId + "/receive", null, admin), 200);
        assertThat(received.path("status").asString()).isEqualTo("RECEIVED");
        assertThat(received.path("statusChanged").asBoolean()).isTrue();
        assertThat(received.path("receivedAt").asString()).isNotBlank();

        // 같은 상세를 다시 열어도 상태가 더 움직이지 않는다.
        var again = data(send("POST", "/api/v1/admin/reports/" + reportId + "/receive", null, admin), 200);
        assertThat(again.path("statusChanged").asBoolean()).isFalse();

        var inProgress = data(send("PUT", "/api/v1/admin/reports/" + reportId + "/memo",
                "{\"adminNote\":\"안전요원 2명 이동 중\"}", admin), 200);
        assertThat(inProgress.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(inProgress.path("adminNote").asString()).isEqualTo("안전요원 2명 이동 중");

        var completed = data(send("POST", "/api/v1/admin/reports/" + reportId + "/complete",
                "{\"adminNote\":\"처리 완료했습니다.\"}", admin), 200);
        assertThat(completed.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(completed.path("completedAt").asString()).isNotBlank();

        // 종결된 신고는 더 바꿀 수 없다.
        assertThat(error(send("POST", "/api/v1/admin/reports/" + reportId + "/complete", "{}", admin), 409))
                .isEqualTo("R003");

        // 방문자도 바뀐 상태와 처리 메모를 본다. 전화번호는 응답에 없다.
        var mine = data(get("/api/v1/reports/" + reportId, token), 200);
        assertThat(mine.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(mine.path("adminNote").asString()).isEqualTo("처리 완료했습니다.");
        assertThat(mine.has("phoneNumber")).isFalse();

        // 상태가 바뀔 때마다 알림이 쌓였는지 확인한다(접수·처리 중·완료).
        var notifications = data(get("/api/v1/notifications", token), 200);
        long forThisReport = 0;
        for (JsonNode n : notifications.path("content"))
            if (n.path("reportId").asLong(0) == reportId) forThisReport++;
        assertThat(forThisReport).isGreaterThanOrEqualTo(3);
    }

    @Test
    void completingDirectlyStillRecordsReceivedTime() throws Exception {
        String token = UUID.randomUUID().toString();
        long reportId = register(token, "FACILITY", "화장실 문이 안 잠겨요");
        var admin = login();

        var completed = data(send("POST", "/api/v1/admin/reports/" + reportId + "/complete", "{}", admin), 200);
        assertThat(completed.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(completed.path("receivedAt").asString()).isNotBlank();
        assertThat(completed.path("inProgressAt").isNull()).isTrue();
    }

    @Test
    void adminListFiltersByCategoryAcrossAllPages() throws Exception {
        String token = UUID.randomUUID().toString();
        register(token, "LOST_ITEM", "가방을 잃어버렸어요");
        var admin = login();

        var filtered = data(send("GET", "/api/v1/admin/reports?category=LOST_ITEM&size=50", null, admin), 200);
        assertThat(filtered.path("content").size()).isGreaterThanOrEqualTo(1);
        for (JsonNode r : filtered.path("content"))
            assertThat(r.path("category").asString()).isEqualTo("LOST_ITEM");

        // summary는 조건과 무관하게 축제 전체 기준이라 신청 건수가 남아 있어야 한다.
        assertThat(filtered.path("summary").has("submitted")).isTrue();
    }

    @Test
    void createdAtIsReturnedInKoreanTime() throws Exception {
        String token = UUID.randomUUID().toString();
        long reportId = register(token, "OTHER", "시간대 확인");
        var created = data(get("/api/v1/reports/" + reportId, token), 200);

        OffsetDateTime createdAt = OffsetDateTime.parse(created.path("createdAt").asString());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9));
        assertThat(createdAt.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        // 9시간 어긋나던 버그를 잡기 위해 실제 시각과의 차이를 본다.
        assertThat(Math.abs(java.time.Duration.between(createdAt, now).toMinutes())).isLessThan(5);
    }

    // ---------------------------------------------------------------- 도우미

    private long register(String token, String category, String title) throws Exception {
        var body = """
                {"category":"%s","title":"%s","reporterName":"테스터","phoneNumber":"01000000000",
                 "place":"청심대 앞","content":"통합 테스트","privacyConsent":true}""".formatted(category, title);
        var res = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/reports")))
                .header("Content-Type", "application/json").header("X-Client-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        return data(res, 201).path("reportId").asLong();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path)))
                .header("X-Client-Token", token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private Session login() throws Exception {
        var res = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/admin/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"loginId\":\"report-super\",\"password\":\"super-password-1\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        var cookies = res.headers().allValues("Set-Cookie").stream().map(c -> c.split(";", 2)[0]).toList();
        var csrf = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.substring(11))
                .findFirst().orElseThrow();
        return new Session(cookies, csrf);
    }

    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (session != null)
            b.header("Cookie", String.join("; ", session.cookies())).header("X-XSRF-TOKEN", session.csrf());
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
