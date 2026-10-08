package RiMarket.com.example.demo.module.catalog.controller.management;

import RiMarket.com.example.demo.module.catalog.port.ImageStorage;
import RiMarket.com.example.demo.module.catalog.port.StoredImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/management/test/images")
@RequiredArgsConstructor
@Slf4j
@Profile("cloudinary-test")
@PreAuthorize("hasRole('ADMIN')")
public class CloudinaryTestController {

    private final ImageStorage imageStorage;

    @PostMapping
    public ResponseEntity<StoredImage> upload(@RequestPart("file") MultipartFile file) throws IOException {

        long start = System.currentTimeMillis();
        StoredImage response = imageStorage.upload(file.getBytes(), "rimarket/test");

        log.info("Cloudinary upload time:" + (System.currentTimeMillis() - start));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam String publicId) {

        imageStorage.delete(publicId);

        return ResponseEntity.noContent().build();
    }
}
