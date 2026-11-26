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

/**
 * 운영진이 입력하는 내용의 영어 칸.
 *
 * <p>프런트는 {@code GET /festival} 응답에 {@code nameEn} 키가 있는지로 서버의 영어 지원 여부를
 * 판단하고, 그 키가 보이면 관리자 화면에 영어 입력칸을 띄웁니다. 그래서 이 키가 나오는 것과
 * 등록·수정이 실제로 영어를 저장하는 것이 같이 맞아야 합니다. 한쪽만 되면 운영진이 입력한
 * 영어가 조용히 사라집니다.
 *
 * <p>영어 칸은 "안 보냄(유지)"과 "빈 문자열(지움)"을 구분합니다. 한국어 칸에는 없던 규칙이라
 * 따로 확인합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnglishTextIntegrationTest {
    @LocalServerPort int port;
    @Autowired FestivalRepository festivals;
    @Autowired AdminAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    record Session(List<String> cookies, String csrf) {}

    private long festivalId;

    @BeforeAll
    void fixture() {
        accounts.saveAndFlush(AdminAccount.builder().loginId("english-super")
                .passwordHash(encoder.encode("super-password-1"))
                .name("상황실").role(AdminRole.SUPER_ADMIN).build());
        accounts.saveAndFlush(AdminAccount.builder().loginId("english-booth")
                .passwordHash(encoder.encode("booth-password-1"))
                .name("부스 운영자").role(AdminRole.BOOTH_ADMIN).build());
        festivalId = festivals.saveAndFlush(Festival.builder().name("영어 테스트 축제")
                .startAt(LocalDateTime.of(2026, 10, 28, 9, 0))
                .endAt(LocalDateTime.of(2026, 10, 30, 23, 59)).build()).getId();
    }

    @Test
    void festivalAlwaysCarriesTheEnglishNameKey() throws Exception {
        var festival = data(get("/api/v1/festival"), 200);
        // 값이 없어도 키는 있어야 합니다. 이 키가 프런트의 영어 지원 판별 기준입니다.
        assertThat(festival.has("nameEn")).isTrue();
    }

    @Test
    void boothKeepsEnglishThroughRegisterAndVisitorView() throws Exception {
        var admin = login("english-super", "super-password-1");
        var created = data(send("POST", "/api/v1/admin/booths", """
                {"festivalId":%d,"name":"총학생회 주점","nameEn":"Student Council Pub",
                 "organizerType":"STUDENT_COUNCIL","period":"DAY","place":"청심대 앞","placeEn":"In front of Cheongsimdae",
                 "description":"닭꼬치와 맥주를 팝니다.","descriptionEn":"We sell chicken skewers and beer."}
                """.formatted(festivalId), admin), 201);
        long boothId = created.path("boothId").asLong();
        assertThat(created.path("nameEn").asString()).isEqualTo("Student Council Pub");

        var visitor = data(get("/api/v1/booths/" + boothId), 200);
        assertThat(visitor.path("nameEn").asString()).isEqualTo("Student Council Pub");
        assertThat(visitor.path("placeEn").asString()).isEqualTo("In front of Cheongsimdae");
        assertThat(visitor.path("descriptionEn").asString()).isEqualTo("We sell chicken skewers and beer.");
        // 한국어는 그대로 남습니다. 영어는 덧붙는 것이지 대체하는 것이 아닙니다.
        assertThat(visitor.path("name").asString()).isEqualTo("총학생회 주점");
    }

    @Test
    void omittedEnglishIsKeptAndEmptyStringClearsIt() throws Exception {
        var admin = login("english-super", "super-password-1");
        long boothId = data(send("POST", "/api/v1/admin/booths", """
                {"festivalId":%d,"name":"사진관","nameEn":"Photo Studio","organizerType":"CLUB",
                 "period":"DAY","place":"학생회관","placeEn":"Student Union"}
                """.formatted(festivalId), admin), 201).path("boothId").asLong();

        // 영어를 보내지 않으면 기존 값을 유지합니다.
        var kept = data(send("PATCH", "/api/v1/admin/booths/" + boothId, "{\"name\":\"사진관 2호\"}", admin), 200);
        assertThat(kept.path("nameEn").asString()).isEqualTo("Photo Studio");
        assertThat(kept.path("placeEn").asString()).isEqualTo("Student Union");

        // 빈 문자열은 운영진이 영어를 지웠다는 뜻이라 null로 저장합니다.
        var cleared = data(send("PATCH", "/api/v1/admin/booths/" + boothId, "{\"nameEn\":\"\"}", admin), 200);
        assertThat(cleared.path("nameEn").isNull()).isTrue();
        assertThat(cleared.path("placeEn").asString()).isEqualTo("Student Union");
    }

    @Test
    void englishLongerThanTheColumnIsRejected() throws Exception {
        var admin = login("english-super", "super-password-1");
        String tooLong = "a".repeat(41);
        assertThat(error(send("POST", "/api/v1/admin/booths", """
                {"festivalId":%d,"name":"긴 이름","nameEn":"%s","organizerType":"CLUB","period":"DAY","place":"어딘가"}
                """.formatted(festivalId, tooLong), admin), 400)).isEqualTo("C001");
    }

    @Test
    void noticeAndStageCarryEnglishToVisitors() throws Exception {
        var admin = login("english-super", "super-password-1");
        data(send("POST", "/api/v1/admin/stages", """
                {"festivalId":%d,"name":"노천극장","nameEn":"Open-air Theater"}
                """.formatted(festivalId), admin), 201);
        data(send("POST", "/api/v1/admin/notices", """
                {"title":"우천 시 안내","titleEn":"In case of rain","content":"공연이 미뤄집니다.",
                 "contentEn":"Performances will be delayed.","category":"INFO"}
                """, admin), 201);

        var stages = data(get("/api/v1/stages"), 200);
        assertThat(stages.isArray()).isTrue();
        boolean found = false;
        for (JsonNode s : stages) if ("Open-air Theater".equals(s.path("nameEn").asString())) found = true;
        assertThat(found).as("무대 목록에 영어 이름이 나와야 합니다").isTrue();

        // 다른 테스트가 만든 공지가 섞이므로 제목으로 찾습니다.
        var notices = data(get("/api/v1/notices"), 200);
        JsonNode mine = null;
        for (JsonNode n : notices) if ("우천 시 안내".equals(n.path("title").asString())) mine = n;
        assertThat(mine).as("방금 등록한 공지가 목록에 있어야 합니다").isNotNull();
        assertThat(mine.path("titleEn").asString()).isEqualTo("In case of rain");
        assertThat(mine.path("contentEn").asString()).isEqualTo("Performances will be delayed.");
    }

    @Test
    void translateIsUnavailableWithoutAKeyButNeverBlocksRegistration() throws Exception {
        var admin = login("english-super", "super-password-1");
        // 테스트에는 DeepL 키가 없습니다. 운영에서도 키를 넣기 전까지 같은 응답이 나갑니다.
        assertThat(error(send("POST", "/api/v1/admin/translate",
                "{\"texts\":[\"총학생회 주점\"],\"target\":\"EN\"}", admin), 503)).isEqualTo("T001");

        // 번역이 안 되더라도 영어를 직접 넣은 등록은 그대로 돼야 합니다.
        var created = data(send("POST", "/api/v1/admin/booths", """
                {"festivalId":%d,"name":"떡볶이집","nameEn":"Tteokbokki Stand","organizerType":"CLUB",
                 "period":"NIGHT","place":"운동장"}
                """.formatted(festivalId), admin), 201);
        assertThat(created.path("nameEn").asString()).isEqualTo("Tteokbokki Stand");
    }

    @Test
    void translateChecksItsInputBeforeCallingOutside() throws Exception {
        var admin = login("english-super", "super-password-1");
        // 11개는 한도(10개)를 넘습니다. 바깥을 부르기 전에 걸러야 합니다.
        String eleven = "[" + "\"가\",".repeat(10) + "\"가\"]";
        assertThat(error(send("POST", "/api/v1/admin/translate",
                "{\"texts\":" + eleven + ",\"target\":\"EN\"}", admin), 400)).isEqualTo("C001");
        assertThat(error(send("POST", "/api/v1/admin/translate",
                "{\"texts\":[\"가\"],\"target\":\"JA\"}", admin), 400)).isEqualTo("C001");
        assertThat(error(send("POST", "/api/v1/admin/translate",
                "{\"texts\":[],\"target\":\"EN\"}", admin), 400)).isEqualTo("C001");
    }

    @Test
    void boothAdminMayTranslateButOnlySuperAdminMayBackfill() throws Exception {
        var booth = login("english-booth", "booth-password-1");
        // 부스 운영자도 자기 부스를 고치므로 번역은 쓸 수 있어야 합니다(키가 없어 503까지 갑니다).
        assertThat(error(send("POST", "/api/v1/admin/translate",
                "{\"texts\":[\"가\"],\"target\":\"EN\"}", booth), 503)).isEqualTo("T001");
        // 일괄 번역은 한 번만 돌리는 작업이라 총괄 관리자만 됩니다.
        assertThat(error(send("POST", "/api/v1/admin/translate/backfill", null, booth), 403)).isEqualTo("A003");
    }

    // ---------------------------------------------------------------- 도우미

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Session login(String loginId, String password) throws Exception {
        var res = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/admin/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password))).build(),
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
