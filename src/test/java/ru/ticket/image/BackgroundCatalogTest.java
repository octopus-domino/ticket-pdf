package ru.ticket.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import ru.ticket.common.ApiException;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundCatalogTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
  @Test void libraryArtworkIsDecodedOnlyAtExportWithIdenticalPixels() throws Exception {
    var catalog = new BackgroundCatalog(directory.toString());
    var file = directory.resolve("sage-paper.webp").toFile();
    try(var input=getClass().getResourceAsStream("/backgrounds/fixture.webp")) {
      java.nio.file.Files.copy(input,file.toPath());
    }
    var expected = ImageIO.read(file);
    String encoded = catalog.image("sage-paper");
    assertSame(encoded, catalog.image("sage-paper"));
    var actual = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded.substring(encoded.indexOf(',')+1))));
    assertEquals(expected.getWidth(), actual.getWidth());
    assertEquals(expected.getHeight(), actual.getHeight());
    for(int y=0;y<expected.getHeight();y++)
      assertArrayEquals(expected.getRGB(0,y,expected.getWidth(),1,null,0,expected.getWidth()), actual.getRGB(0,y,actual.getWidth(),1,null,0,actual.getWidth()));
  }

  @Test void missingAndTraversalIdsCannotReadArbitraryFiles() {
    var catalog = new BackgroundCatalog(".");
    assertThrows(ApiException.class, () -> catalog.image("../pom"));
    assertThrows(ApiException.class, () -> catalog.image("nonexistent-background"));
  }

  @Test void pngOptimisationRemovesOnlyUnusedAlphaAndPreservesRealTransparency() throws Exception {
    var image = new BufferedImage(13,17,BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<17;y++)for(int x=0;x<13;x++)image.setRGB(x,y,0xff193c91);
    var opaque = ImageIO.read(new ByteArrayInputStream(LosslessPng.encode(image)));
    assertFalse(opaque.getColorModel().hasAlpha());
    image.setRGB(2,3,0x80193c91);
    var transparent = ImageIO.read(new ByteArrayInputStream(LosslessPng.encode(image)));
    assertEquals(image.getRGB(2,3), transparent.getRGB(2,3));
  }
}
