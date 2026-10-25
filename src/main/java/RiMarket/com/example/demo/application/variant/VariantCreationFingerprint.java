package RiMarket.com.example.demo.application.variant;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Stable, versioned fingerprint of the operation's meaning, not raw HTTP JSON. */
final class VariantCreationFingerprint {
    private VariantCreationFingerprint() {}

    static String of(UUID productId, CreateVariantWithStockRequest request) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                writeField(data, "create-variant-v1");
                writeField(data, productId.toString());
                writeField(data, request.name().trim());
                writeField(data, request.price().stripTrailingZeros().toPlainString());
                data.writeInt(request.initialQuantity());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint variant creation", exception);
        }
    }

    private static void writeField(DataOutputStream data, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }
}
