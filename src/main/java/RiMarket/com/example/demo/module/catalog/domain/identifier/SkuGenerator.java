package RiMarket.com.example.demo.module.catalog.domain.identifier;

import RiMarket.com.example.demo.module.shared.text.AsciiNormalizer;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class SkuGenerator {
    //Generate smart Sku base on productName + productVariantName + random UUID(Lenovo IdeaPad Gaming 3 && 16GB 512GB -> LENOVO-IDEAPAD-GAMIN-16GB-512GB-A83F91C2)
    public String generate(String productName, String variantName) {
        String productPart = normalize(productName, 20);

        String variantPart = normalize(variantName, 20);

        String entropy = UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase(Locale.ROOT);

        return productPart + "-" + variantPart + "-" + entropy;
    }


    //From normal text to sku text format(Laptop do hoa -> LAPTOP-DO-HOA)
    private String normalize(String value, int maxLength) {
        String normalized = AsciiNormalizer.normalize(value)
                        .toUpperCase(Locale.ROOT)
                        .replaceAll("[^A-Z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");

        if (normalized.length() <= maxLength) {
            return normalized;
        }
        String truncated = normalized.substring(0, maxLength);

        return truncated.replaceAll("-$", "");
    }
}
