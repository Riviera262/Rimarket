package RiMarket.com.example.demo.module.catalog.controller.management;

import RiMarket.com.example.demo.module.catalog.dto.imageVariant.VariantImageResponse;
import RiMarket.com.example.demo.module.catalog.service.VariantImageService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Checks multipart binding and JSON shape; authentication is outside this standalone MVC test. */
class VariantImageManagementControllerTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void acceptsOneOrMoreFilesAtTheSameEndpointAndAlwaysReturnsAnArray(int count) throws Exception {
        var service = mock(VariantImageService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new VariantImageManagementController(service)).build();
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        var request = multipart("/api/management/products/{productId}/variants/{variantId}/images", productId, variantId);
        List<VariantImageResponse> responses = new ArrayList<>();
        List<String> filenames = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String filename = "image-" + index + ".jpg";
            filenames.add(filename);
            request.file(new MockMultipartFile("files", filename, "image/jpeg", new byte[]{(byte) index}));
            responses.add(new VariantImageResponse(UUID.randomUUID(), "https://cdn.test/" + filename, index));
        }
        when(service.uploadImages(eq(productId), eq(variantId), anyList())).thenReturn(responses);

        mvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(count))
                .andExpect(jsonPath("$[0].url").value(responses.getFirst().url()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(service).uploadImages(eq(productId), eq(variantId), files.capture());
        assertEquals(filenames, files.getValue().stream().map(MultipartFile::getOriginalFilename).toList());
    }
}
