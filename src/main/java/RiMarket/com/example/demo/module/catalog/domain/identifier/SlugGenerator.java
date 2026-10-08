package RiMarket.com.example.demo.module.catalog.domain.identifier;

import RiMarket.com.example.demo.module.shared.text.AsciiNormalizer;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public final class SlugGenerator {

    private static final int MAX_LENGTH = 255;
    private static final int ENTROPY_LENGTH = 8;

    //Turn normal text to follow slug policy text(Laptop do hoa -> laptop-do-hoa)
    public String generate(String productName){
        return AsciiNormalizer.normalize(productName)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    //If the baseSlug got duplicate, attach more UUID to it(laptop-do-hoa -> laptop-do-hoa-a83f29c1)
    public String appendEntropy(String baseSlug) {

        String entropy = UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, ENTROPY_LENGTH)
                        .toLowerCase(Locale.ROOT);

        int maxBaseLength = MAX_LENGTH - ENTROPY_LENGTH - 1;

        String safeBase = truncate(baseSlug, maxBaseLength).replaceAll("-+$", "");

        return safeBase + "-" + entropy;
    }

    //Shorten the baseSlug if it too long to make more room for appendEntropy to attach with it
    private String truncate(String value, int maxLength) {

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}