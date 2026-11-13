package com.example.kufim;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.festival.entity.Festival;
import com.example.kufim.festival.repository.FestivalRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import javax.imageio.ImageIO;
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
 * 분실물센터의 등록 → 인계 → 취소 → 삭제 흐름과 개인정보 노출 범위.
 *
 * 특히 두 가지를 못 박아 둡니다.
 * 민감 분류(신분증·지갑)는 요청이 공개를 원해도 방문자에게 사진을 주지 않고,
 * 인계가 끝난 물건은 방문자 상세·사진이 없는 물건과 똑같은 404여야 합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LostItemIntegrationTest {
    @LocalServerPort int port;
    @Autowired FestivalRepository festivals;
    @Autowired AdminAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    record Session(List<String> cookies, String csrf) {}

    @BeforeAll
    void fixture() {
        accounts.saveAndFlush(AdminAccount.builder().loginId("lost-super")
                .passwordHash(encoder.encode("super-password-1"))
                .name("상황실").role(AdminRole.SUPER_ADMIN).build());
        accounts.saveAndFlush(AdminAccount.builder().loginId("lost-booth")
                .passwordHash(encoder.encode("booth-password-1"))
                .name("부스 운영자").role(AdminRole.BOOTH_ADMIN).build());
        festivals.saveAndFlush(Festival.builder().name("분실물 테스트 축제")
                .startAt(LocalDateTime.of(2026, 10, 28, 9, 0))
                .endAt(LocalDateTime.of(2026, 10, 30, 23, 59)).build());
    }

    @Test
    void registerHandOverCancelAndDelete() throws Exception {
        var admin = login("lost-super", "super-password-1");
        String imageKey = upload(admin);
        long id = create(admin, "ELECTRONICS", "검은색 에어팟 케이스", imageKey, true);

        // 방문자 목록·상세에서 보이고 사진도 열린다.
        var listed = data(get("/api/v1/lost-items?q=에어팟"), 200);
        assertThat(listed.path("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        var detail = data(get("/api/v1/lost-items/" + id), 200);
        assertThat(detail.path("name").asString()).isEqualTo("검은색 에어팟 케이스");
        assertThat(detail.path("imageUrl").asString()).contains("/api/v1/lost-items/" + id + "/image");
        assertThat(image("/api/v1/lost-items/" + id + "/image?size=thumb").statusCode()).isEqualTo(200);

        // 인계 완료
        var returned = data(send("POST", "/api/v1/admin/lost-items/" + id + "/return",
                "{\"receiverName\":\"김건국\",\"receiverPhone\":\"01012341234\",\"verifiedBy\":\"STUDENT_ID\",\"returnNote\":\"학생증 확인\"}",
                admin), 200);
        assertThat(returned.path("status").asString()).isEqualTo("RETURNED");
        assertThat(returned.path("receiverName").asString()).isEqualTo("김건국");
        assertThat(returned.path("receiverPhoneMasked").asString()).isEqualTo("010-****-1234");
        assertThat(error(send("POST", "/api/v1/admin/lost-items/" + id + "/return",
                "{\"receiverName\":\"김건국\",\"verifiedBy\":\"OTHER\"}", admin), 409)).isEqualTo("L002");

        // 인계가 끝나면 방문자에게는 없는 물건과 같다.
        assertThat(error(get("/api/v1/lost-items/" + id), 404)).isEqualTo("L001");
        assertThat(image("/api/v1/lost-items/" + id + "/image").statusCode()).isEqualTo(404);

        // 목록에는 남되 상세 내용은 비어 있다.
        var closed = findInList("/api/v1/lost-items?status=ALL&size=50", id);
        assertThat(closed.path("status").asString()).isEqualTo("RETURNED");
        assertThat(closed.path("foundPlace").isNull()).isTrue();
        assertThat(closed.path("imageUrl").isNull()).isTrue();
        assertThat(closed.has("elapsedMinutes")).isTrue();

        // 연락처 조회
        assertThat(data(send("GET", "/api/v1/admin/lost-items/" + id + "/contact", null, admin), 200)
                .path("phoneNumber").asString()).isEqualTo("01012341234");

        // 인계 취소하면 다시 보인다.
        var reopened = data(send("POST", "/api/v1/admin/lost-items/" + id + "/reopen", null, admin), 200);
        assertThat(reopened.path("status").asString()).isEqualTo("STORED");
        assertThat(reopened.path("receiverName").isNull()).isTrue();
        assertThat(reopened.path("returnedAt").isNull()).isTrue();
        assertThat(data(get("/api/v1/lost-items/" + id), 200).path("name").asString())
                .isEqualTo("검은색 에어팟 케이스");

        // 삭제하면 양쪽 모두에서 사라진다.
        assertThat(send("DELETE", "/api/v1/admin/lost-items/" + id, null, admin).statusCode()).isEqualTo(204);
        assertThat(error(get("/api/v1/lost-items/" + id), 404)).isEqualTo("L001");
        assertThat(error(send("GET", "/api/v1/admin/lost-items/" + id, null, admin), 404)).isEqualTo("L001");
    }

    @Test
    void sensitiveCategoryNeverExposesPhotoToVisitors() throws Exception {
        var admin = login("lost-super", "super-password-1");
        String imageKey = upload(admin);
        // 공개를 요청해도 신분증은 비공개로 저장된다.
        long id = create(admin, "ID_CARD", "학생증", imageKey, true);

        var adminView = data(send("GET", "/api/v1/admin/lost-items/" + id, null, admin), 200);
        assertThat(adminView.path("imagePublic").asBoolean()).isFalse();
        assertThat(adminView.path("imageUrl").asString()).contains("/admin/");

        var visitorView = data(get("/api/v1/lost-items/" + id), 200);
        assertThat(visitorView.path("imageUrl").isNull()).isTrue();
        assertThat(visitorView.path("thumbnailUrl").isNull()).isTrue();
        assertThat(image("/api/v1/lost-items/" + id + "/image").statusCode()).isEqualTo(404);

        // 관리자는 볼 수 있다.
        assertThat(imageAs("/api/v1/admin/lost-items/" + id + "/image", admin).statusCode()).isEqualTo(200);
    }

    @Test
    void photoIsKeptReplacedOrRemovedByImageKeyField() throws Exception {
        var admin = login("lost-super", "super-password-1");
        long id = create(admin, "CLOTHING", "검정 패딩", upload(admin), true);

        // 필드를 빼면 사진이 그대로 있다.
        var kept = data(send("PATCH", "/api/v1/admin/lost-items/" + id,
                "{\"name\":\"검정 롱패딩\"}", admin), 200);
        assertThat(kept.path("name").asString()).isEqualTo("검정 롱패딩");
        assertThat(kept.path("imageUrl").isNull()).isFalse();

        // 문자열이면 교체된다.
        var replaced = data(send("PATCH", "/api/v1/admin/lost-items/" + id,
                "{\"imageKey\":\"" + upload(admin) + "\"}", admin), 200);
        assertThat(replaced.path("imageUrl").isNull()).isFalse();

        // null이면 사진이 사라진다.
        var cleared = data(send("PATCH", "/api/v1/admin/lost-items/" + id,
                "{\"imageKey\":null}", admin), 200);
        assertThat(cleared.path("imageUrl").isNull()).isTrue();
        assertThat(image("/api/v1/lost-items/" + id + "/image").statusCode()).isEqualTo(404);
    }

    @Test
    void uploadRejectsNonImageAndUnknownKeyIsRefused() throws Exception {
        var admin = login("lost-super", "super-password-1");
        assertThat(errorOf(multipart("/api/v1/admin/lost-items/images",
                "그냥 글자".getBytes(StandardCharsets.UTF_8), admin), 400)).isEqualTo("L003");
        assertThat(error(send("POST", "/api/v1/admin/lost-items",
                """
                {"category":"OTHER","name":"우산","foundPlace":"정문","foundAt":"2026-10-28T13:00:00+09:00",
                 "imageKey":"lost-items/2026/없는키.jpg"}""", admin), 400)).isEqualTo("L003");
    }

    @Test
    void boothAdminCannotReachLostItems() throws Exception {
        var booth = login("lost-booth", "booth-password-1");
        assertThat(error(send("GET", "/api/v1/admin/lost-items", null, booth), 403)).isEqualTo("A003");
    }

    @Test
    void foundAtInTheFutureIsRejected() throws Exception {
        var admin = login("lost-super", "super-password-1");
        assertThat(error(send("POST", "/api/v1/admin/lost-items",
                """
                {"category":"OTHER","name":"미래 물건","foundPlace":"정문","foundAt":"2030-01-01T10:00:00+09:00"}""",
                admin), 400)).isEqualTo("C001");
    }

    // ---------------------------------------------------------------- 도우미

    private long create(Session admin, String category, String name, String imageKey, boolean imagePublic)
            throws Exception {
        String body = """
                {"category":"%s","name":"%s","foundPlace":"노천극장 객석 3열",
                 "foundAt":"2026-10-28T17:20:00+09:00","description":"테스트 등록",
                 "imageKey":%s,"imagePublic":%s}"""
                .formatted(category, name, imageKey == null ? "null" : "\"" + imageKey + "\"", imagePublic);
        return data(send("POST", "/api/v1/admin/lost-items", body, admin), 201).path("lostItemId").asLong();
    }

    private String upload(Session admin) throws Exception {
        var res = multipart("/api/v1/admin/lost-items/images", samplePhoto(), admin);
        return data(res, 201).path("imageKey").asString();
    }

    /** 가로가 긴 작은 JPEG. 저장 과정에서 축소와 EXIF 제거를 거칩니다. */
    private byte[] samplePhoto() throws Exception {
        BufferedImage image = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, 1600, 900);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private HttpResponse<String> multipart(String path, byte[] content, Session session) throws Exception {
        String boundary = "----kufim-test-boundary";
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Cookie", String.join("; ", session.cookies()))
                .header("X-XSRF-TOKEN", session.csrf())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode findInList(String path, long lostItemId) throws Exception {
        for (JsonNode item : data(get(path), 200).path("content"))
            if (item.path("lostItemId").asLong() == lostItemId) return item;
        throw new AssertionError("목록에서 " + lostItemId + "번을 찾지 못했습니다.");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> image(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> imageAs(String path, Session session) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path)))
                .header("Cookie", String.join("; ", session.cookies())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private Session login(String loginId, String password) throws Exception {
        var res = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/admin/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}")).build(),
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

    private String errorOf(HttpResponse<String> res, int status) {
        return error(res, status);
    }

    private String url(String path) { return "http://localhost:" + port + path; }
}
