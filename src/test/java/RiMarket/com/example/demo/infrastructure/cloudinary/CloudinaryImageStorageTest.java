package RiMarket.com.example.demo.infrastructure.cloudinary;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CloudinaryImageStorageTest {
    private final Cloudinary cloudinary = mock(Cloudinary.class, RETURNS_DEEP_STUBS);
    private final CloudinaryImageStorage storage = new CloudinaryImageStorage(cloudinary);

    @Test
    void uploadNeedsOnlyAssetIdAndSecureUrl() throws IOException {
        when(cloudinary.uploader().upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("public_id", "asset", "secure_url", "https://cdn.test/asset"));
        var result = storage.upload(new byte[]{1}, "folder");
        assertEquals("asset", result.publicId());
        assertEquals("https://cdn.test/asset", result.url());
    }

    @Test
    void deleteAcceptsConfirmedRemovalAndAlreadyAbsentAsset() throws IOException {
        when(cloudinary.uploader().destroy(eq("asset"), anyMap()))
                .thenReturn(Map.of("result", "ok"), Map.of("result", "not found"));
        assertDoesNotThrow(() -> storage.delete("asset"));
        assertDoesNotThrow(() -> storage.delete("asset"));
        verify(cloudinary.uploader(), times(2)).destroy("asset",
                Map.of("resource_type", "image", "invalidate", true));
    }

    @Test
    void deleteRejectsUnconfirmedResult() throws IOException {
        when(cloudinary.uploader().destroy(eq("asset"), anyMap()))
                .thenReturn(Map.of("result", "unexpected"));
        assertThrows(ImageStorageException.class, () -> storage.delete("asset"));
    }

    @Test
    void storageIoFailureIsWrapped() throws IOException {
        when(cloudinary.uploader().destroy(eq("asset"), anyMap()))
                .thenThrow(new IOException("simulated failure"));
        assertInstanceOf(IOException.class,
                assertThrows(ImageStorageException.class, () -> storage.delete("asset")).getCause());
    }
}
