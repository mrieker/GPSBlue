
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class BlandChartFade {
    public static void main (String[] args)
            throws Exception
    {
        BufferedImage img = ImageIO.read (new File ("bland_chart_512.png"));
        int w = img.getWidth ();
        int h = img.getHeight ();
        int[] pixels = img.getRGB (0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y ++) {
            double dy = y * 2.0 / h - 1.0;
            for (int x = 0; x < w; x ++) {
                double dx = x * 2.0 / w - 1.0;
                double cos = Math.cos (Math.hypot (dx, dy) * Math.PI / 2.0);
                int alpha = (cos < 0) ? 0 : (int) (Math.sqrt (Math.sqrt (cos)) * 255);
                int i = y * w + x;
                pixels[i] = (pixels[i] & 0x00FFFFFF) | (alpha << 24);
            }
        }
        img.setRGB (0, 0, w, h, pixels, 0, w);
        ImageIO.write (img, "png", new File ("bland_faded_512.png"));
    }
}
