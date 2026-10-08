package RiMarket.com.example.demo.module.shared.text;

import java.text.Normalizer;

public final class AsciiNormalizer {

    private AsciiNormalizer() {
    }

    //Turn Vietnamese to Normal text(Laptop đồ họa -> Laptop do hoa)
    public static String normalize(String input) {

        String normalized = Normalizer.normalize(
                input,
                Normalizer.Form.NFD
        );

        normalized = normalized
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');

        return normalized;
    }
}