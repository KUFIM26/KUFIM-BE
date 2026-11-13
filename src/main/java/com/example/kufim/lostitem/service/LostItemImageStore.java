package com.example.kufim.lostitem.service;

import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 습득물 사진을 서버 디스크에 저장하고 읽어 줍니다.
 *
 * DB에는 키만 남기고 파일은 업로드 경로 아래에 둡니다. 저장할 때 항상 JPEG로 다시 쓰기 때문에
 * 원본에 들어 있던 EXIF(촬영 위치 포함)는 남지 않습니다. 원본은 긴 변 1280px, 썸네일은 400px입니다.
 */
@Slf4j
@Component
public class LostItemImageStore {
    private static final int FULL_EDGE = 1280;
    private static final int THUMB_EDGE = 400;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String THUMB_SUFFIX = "-thumb";
    private static final String EXTENSION = ".jpg";

    private final Path root;

    public LostItemImageStore(@Value("${app.lost-item.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** 업로드를 받아 원본과 썸네일을 저장하고 저장소 키를 돌려줍니다. */
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ContractException(HttpStatus.BAD_REQUEST, "L003", "지원하지 않는 이미지입니다.");
        if (file.getSize() > MAX_BYTES)
            throw new ContractException(HttpStatus.PAYLOAD_TOO_LARGE, "L004", "이미지는 5MB 이하만 올릴 수 있습니다.");

        byte[] bytes;
        try (InputStream in = file.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new ContractException(HttpStatus.BAD_REQUEST, "L003", "지원하지 않는 이미지입니다.");
        }
        // 확장자나 Content-Type이 아니라 실제 내용으로 판별합니다.
        if (bytes.length > MAX_BYTES)
            throw new ContractException(HttpStatus.PAYLOAD_TOO_LARGE, "L004", "이미지는 5MB 이하만 올릴 수 있습니다.");
        if (!looksLikeImage(bytes))
            throw new ContractException(HttpStatus.BAD_REQUEST, "L003", "지원하지 않는 이미지입니다.");

        BufferedImage source = decode(bytes);
        String key = "lost-items/%d/%s%s".formatted(ApiClock.now().getYear(),
                UUID.randomUUID().toString().replace("-", ""), EXTENSION);
        try {
            Path full = resolve(key);
            Files.createDirectories(full.getParent());
            writeJpeg(scale(source, FULL_EDGE), full);
            writeJpeg(scale(source, THUMB_EDGE), resolve(thumbKey(key)));
        } catch (IOException e) {
            log.error("습득물 사진 저장 실패 key={}", key, e);
            throw new ContractException(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "사진을 저장하지 못했습니다.");
        }
        return key;
    }

    /** 저장된 사진 바이트. 파일이 없으면 비어 있는 결과를 돌려줍니다. */
    public byte[] read(String imageKey, boolean thumbnail) {
        if (imageKey == null) return null;
        Path path = resolve(thumbnail ? thumbKey(imageKey) : imageKey);
        try {
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException e) {
            log.warn("습득물 사진 읽기 실패 key={}", imageKey, e);
            return null;
        }
    }

    public void delete(String imageKey) {
        if (imageKey == null) return;
        for (String key : new String[]{imageKey, thumbKey(imageKey)}) {
            try {
                Files.deleteIfExists(resolve(key));
            } catch (IOException e) {
                log.warn("습득물 사진 삭제 실패 key={}", key, e);
            }
        }
    }

    /** 등록에 쓰이지 않은 채 하루가 지난 파일을 지웁니다. 지운 개수를 돌려줍니다. */
    public int deleteOrphans(Collection<String> keysInUse, Duration olderThan) {
        Set<String> keep = new HashSet<>();
        for (String key : keysInUse) {
            if (key == null) continue;
            keep.add(key);
            keep.add(thumbKey(key));
        }
        if (!Files.isDirectory(root)) return 0;
        long cutoff = System.currentTimeMillis() - olderThan.toMillis();
        int removed = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String key = root.relativize(path).toString().replace('\\', '/');
                if (keep.contains(key)) continue;
                if (path.toFile().lastModified() > cutoff) continue;
                Files.deleteIfExists(path);
                removed++;
            }
        } catch (IOException e) {
            log.warn("습득물 사진 정리 실패", e);
        }
        return removed;
    }

    // ---------------------------------------------------------------- 내부

    private String thumbKey(String imageKey) {
        int dot = imageKey.lastIndexOf('.');
        return dot < 0 ? imageKey + THUMB_SUFFIX
                : imageKey.substring(0, dot) + THUMB_SUFFIX + imageKey.substring(dot);
    }

    /** 키는 서버가 만든 값만 받지만, 경로 탈출은 한 번 더 막습니다. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root))
            throw new ContractException(HttpStatus.BAD_REQUEST, "L003", "지원하지 않는 이미지입니다.");
        return path;
    }

    private boolean looksLikeImage(byte[] b) {
        if (b.length < 12) return false;
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean webp = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpeg || png || webp;
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null)
                throw new ContractException(HttpStatus.BAD_REQUEST, "L003", "지원하지 않는 이미지입니다.");
            return image;
        } catch (IOException e) {
            throw new ContractException(HttpStatus.BAD_REQUEST, "L003", "지원하지 않는 이미지입니다.");
        }
    }

    /** 긴 변을 maxEdge로 맞춥니다. 이미 작으면 그대로 둡니다. 투명 배경은 흰색으로 깔립니다. */
    private BufferedImage scale(BufferedImage source, int maxEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        double ratio = Math.min(1.0, (double) maxEdge / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** 임시 파일에 먼저 쓰고 옮겨, 읽는 쪽이 절반만 쓰인 파일을 보지 않게 합니다. */
    private void writeJpeg(BufferedImage image, Path target) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", buffer))
            throw new IOException("JPEG 인코더를 찾지 못했습니다.");
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "upload", ".part");
        Files.write(temp, buffer.toByteArray());
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** 응답 Content-Type. 저장은 항상 JPEG입니다. */
    public String contentType() {
        return "image/jpeg".toLowerCase(Locale.ROOT);
    }
}
