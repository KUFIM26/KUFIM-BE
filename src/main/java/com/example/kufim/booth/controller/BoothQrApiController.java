package com.example.kufim.booth.controller;

import com.example.kufim.booth.repository.BoothRepository;
import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.security.AdminAccess;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/booths/{boothId}/qr")
@RequiredArgsConstructor
public class BoothQrApiController {
    private final BoothRepository booths;
    private final AdminAccess access;
    @Value("${app.frontend.base-url}") private String baseUrl;

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> issue(@PathVariable long boothId) {
        access.superAdmin();
        var b = booths.lockActive(boothId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND,"B001","부스를 찾을 수 없습니다."));
        b.issueQr(UUID.randomUUID().toString().replace("-", "").substring(0, 12), ApiClock.now());
        return ApiResponse.ok(Map.of("boothId", boothId, "boothCode", b.getBoothCode(), "url", url(b.getBoothCode()),
                "issuedAt", ApiClock.offset(b.getQrIssuedAt())));
    }

    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> png(@PathVariable long boothId) throws Exception {
        access.booth(boothId);
        var b = booths.findByIdAndDeletedFalse(boothId).orElseThrow(() -> new ContractException(HttpStatus.NOT_FOUND,"B001","부스를 찾을 수 없습니다."));
        if (b.getBoothCode() == null) throw new ContractException(HttpStatus.NOT_FOUND,"B002","발급된 QR 코드가 없습니다.");
        var matrix = new QRCodeWriter().encode(url(b.getBoothCode()), BarcodeFormat.QR_CODE, 512, 512);
        var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < matrix.getWidth(); x++) for (int y = 0; y < matrix.getHeight(); y++)
            image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=booth-" + boothId + "-qr.png")
                .body(bytes.toByteArray());
    }
    private String url(String code) { return baseUrl.replaceAll("/+$", "") + "/w/" + code; }
}
