package dio.budgeting.application.ai;

import org.springframework.core.io.Resource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class ReceiptImageNormalizer {

    private ReceiptImageNormalizer() {
    }

    static NormalizedImage normalize(Resource resource, String ignoredMimeType)
            throws IOException {
        var source = ImageIO.read(
                new ByteArrayInputStream(resource.getContentAsByteArray())
        );

        if (source == null) {
            throw new IllegalArgumentException(
                    "The uploaded file is not a readable image."
            );
        }

        var rgbImage = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        var graphics = (Graphics2D) rgbImage.getGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(rgbImage, "jpg", output)) {
            throw new IOException("Could not encode the uploaded image as JPEG.");
        }

        return new NormalizedImage(output.toByteArray(), "image/jpeg");
    }

    record NormalizedImage(byte[] bytes, String mimeType) {
    }
}
