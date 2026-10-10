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
import java.util.ArrayList;
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

/** 목록 조회 최적화(순번 count, 부스별 대기 수, 공지 읽음 일괄 조회)가 결과를 바꾸지 않는지 확인합니다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListPerformanceIntegrationTest {
    @LocalServerPort int port;
    @Autowired FestivalRepository festivals;
    @Autowired BoothRepository booths;
    @Autowired AdminAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();
    private long boothId;
    private List<String> cookies;
    private String csrf;

    @BeforeAll
    void fixture() throws Exception {
        accounts.saveAndFlush(AdminAccount.builder().loginId("list-super").passwordHash(encoder.encode("pw-123456"))
                .name("목록 관리자").role(AdminRole.SUPER_ADMIN).build());
        var festival = festivals.saveAndFlush(Festival.builder().name("목록 축제")
                .startAt(LocalDateTime.of(2026, 10, 1, 9, 0)).endAt(LocalDateTime.of(2026, 12, 31, 23, 0)).build());
        boothId = booths.saveAndFlush(Booth.builder().festival(festival).name("목록 부스").place("청심대")
                .waitingEnabled(true).build()).getId();
        var login = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/admin/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"loginId\":\"list-super\",\"password\":\"pw-123456\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        cookies = login.headers().allValues("Set-Cookie").stream().map(c -> c.split(";", 2)[0]).toList();
        csrf = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).map(c -> c.substring(11)).findFirst().orElseThrow();
    }

    @Test
    void positionsAndBoothCountsStayCorrectAsTheQueueMoves() throws Exception {
        List<String> tokens = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String token = UUID.randomUUID().toString();
            var res = send(HttpRequest.newBuilder(URI.create(url("/api/v1/booths/" + boothId + "/waitings")))
                    .header("Content-Type", "application/json").header("X-Client-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"studentNumber\":\"202600001\",\"phoneNumber\":\"01000000001\",\"partySize\":2,\"privacyConsent\":true}")));
            assertThat(res.statusCode()).as(res.body()).isEqualTo(201);
            tokens.add(token);
            ids.add(json.readTree(res.body()).path("data").path("waitingId").asString());
        }
        assertThat(boothRow().path("waitingTeamCount").asInt()).isEqualTo(4);
        assertThat(myPosition(tokens.get(3))).isEqualTo(4);
        assertThat(adminPositions()).containsExactly(1, 2, 3, 4);

        // 첫 팀 호출: 호출된 팀은 순번이 없고, 뒤의 팀은 한 칸씩 당겨진다
        var call = send(admin("/api/v1/admin/waitings/" + ids.get(0) + "/call").POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(call.statusCode()).as(call.body()).isEqualTo(200);
        assertThat(myPosition(tokens.get(3))).isEqualTo(3);
        assertThat(adminPositions()).containsExactly(null, 1, 2, 3);
        assertThat(boothRow().path("waitingTeamCount").asInt()).isEqualTo(3);
        assertThat(boothRow().path("estimatedWaitMinutes").asInt()).isEqualTo(3 * booths.findById(boothId).orElseThrow().getEstimateMinutesPerTeam());

        // 가운데 팀 취소
        var cancel = send(HttpRequest.newBuilder(URI.create(url("/api/v1/waitings/" + ids.get(1))))
                .header("X-Client-Token", tokens.get(1)).DELETE());
        assertThat(cancel.statusCode()).isEqualTo(204);
        assertThat(myPosition(tokens.get(3))).isEqualTo(2);
    }

    @Test
    void noticeUnreadIsResolvedPerTokenAndCsrfFailuresHaveTheirOwnCode() throws Exception {
        var created = send(admin("/api/v1/admin/notices").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"읽음 확인 공지\",\"content\":\"내용\",\"category\":\"INFO\"}")));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        long noticeId = json.readTree(created.body()).path("data").path("noticeId").asLong();
        String token = UUID.randomUUID().toString();

        assertThat(noticeUnread(token, noticeId)).isTrue();
        assertThat(noticeUnread(null, noticeId)).isNull();
        var page = json.readTree(send(HttpRequest.newBuilder(URI.create(url("/api/v1/notifications"))).header("X-Client-Token", token).GET()).body());
        long notificationId = -1;
        for (var n : page.path("data").path("content")) if (n.path("noticeId").asLong() == noticeId) notificationId = n.path("notificationId").asLong();
        var read = send(HttpRequest.newBuilder(URI.create(url("/api/v1/notifications/" + notificationId + "/read")))
                .header("X-Client-Token", token).method("PATCH", HttpRequest.BodyPublishers.noBody()));
        assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
        assertThat(noticeUnread(token, noticeId)).isFalse();
        assertThat(noticeUnread(UUID.randomUUID().toString(), noticeId)).isTrue();

        // 세션은 있지만 XSRF 헤더가 없으면 A005(다시 로그인), 권한 부족(A003)과 구분된다
        var noCsrf = send(HttpRequest.newBuilder(URI.create(url("/api/v1/admin/notices")))
                .header("Content-Type", "application/json").header("Cookie", String.join("; ", cookies))
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"x\",\"content\":\"x\",\"category\":\"INFO\"}")));
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(json.readTree(noCsrf.body()).path("error").path("code").asString()).isEqualTo("A005");
    }

    private JsonNode boothRow() throws Exception {
        for (var b : json.readTree(send(HttpRequest.newBuilder(URI.create(url("/api/v1/booths"))).GET()).body()).path("data"))
            if (b.path("boothId").asLong() == boothId) return b;
        throw new AssertionError("booth not listed");
    }
    private int myPosition(String token) throws Exception {
        var res = send(HttpRequest.newBuilder(URI.create(url("/api/v1/waitings/me"))).header("X-Client-Token", token).GET());
        return json.readTree(res.body()).path("data").get(0).path("position").asInt();
    }
    private List<Integer> adminPositions() throws Exception {
        var res = send(admin("/api/v1/admin/booths/" + boothId + "/waitings").GET());
        List<Integer> positions = new ArrayList<>();
        for (var w : json.readTree(res.body()).path("data").path("content")) {
            if (!List.of("WAITING", "CALLED").contains(w.path("status").asString())) continue;
            positions.add(w.path("position").isNull() ? null : w.path("position").asInt());
        }
        return positions;
    }
    private Boolean noticeUnread(String token, long noticeId) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url("/api/v1/notices"))).GET();
        if (token != null) b.header("X-Client-Token", token);
        for (var n : json.readTree(send(b).body()).path("data"))
            if (n.path("noticeId").asLong() == noticeId) return n.path("unread").isNull() ? null : n.path("unread").asBoolean();
        throw new AssertionError("notice not listed");
    }
    private HttpRequest.Builder admin(String path) {
        return HttpRequest.newBuilder(URI.create(url(path))).header("Cookie", String.join("; ", cookies)).header("X-XSRF-TOKEN", csrf);
    }
    private HttpResponse<String> send(HttpRequest.Builder b) throws Exception { return client.send(b.build(), HttpResponse.BodyHandlers.ofString()); }
    private String url(String path) { return "http://localhost:" + port + path; }
}
