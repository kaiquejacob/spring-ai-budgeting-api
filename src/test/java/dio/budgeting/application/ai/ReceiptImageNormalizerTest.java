package dio.budgeting.application.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptImageNormalizerTest {

    @Test
    void shouldConvertAValidImageToGeminiCompatibleJpeg() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.WHITE.getRGB());

        var input = new ByteArrayOutputStream();
        ImageIO.write(image, "png", input);

        var normalized = ReceiptImageNormalizer.normalize(
                new ByteArrayResource(input.toByteArray()),
                "image/png"
        );

        assertEquals("image/jpeg", normalized.mimeType());
        assertTrue(normalized.bytes().length > 0);
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(normalized.bytes())));
    }
}
