package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import ru.ticket.dto.*;

@DataJpaTest
class JourneyMediaTest {
  @Autowired JourneyMediaRepository repository;

  @Test
  void imagesAreDeduplicatedAndTransparentLogosRemainTransparent() throws Exception {
    var service = new JourneyMediaService(repository, 1);
    String input = image(40, 20, true);
    var first = service.save(input);
    assertThat(service.save(input)).isEqualTo(first);
    assertThat(repository.count()).isEqualTo(1);
    var decoded = ImageIO.read(new ByteArrayInputStream(service.get(first.id()).content));
    assertThat(decoded.getColorModel().hasAlpha()).isTrue();
    assertThat(decoded.getRGB(0, 0) >>> 24).isEqualTo(128);
    assertThatThrownBy(() -> service.save(image(41, 20, false))).hasMessage("journey.media.full");
  }

  @Test
  void oversizedBackgroundIsRejectedBeforeTheDatabaseAndLosslessSmallBackgroundFits() throws Exception {
    var service = new JourneyMediaService(repository, 2);
    var image = new BufferedImage(800,800,BufferedImage.TYPE_INT_RGB);
    var random = new Random(123);
    for(int y=0;y<800;y++)for(int x=0;x<800;x++)image.setRGB(x,y,random.nextInt(0x1000000));
    var bytes = new ByteArrayOutputStream(); ImageIO.write(image,"png",bytes);
    assertThatThrownBy(()->service.saveBackground("data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray())))
        .hasMessage("journey.media.invalid");
    assertThat(repository.count()).isZero();
    var asset = service.saveBackground(image(40,20,false));
    var restored = ImageIO.read(new ByteArrayInputStream(service.get(asset.id()).content));
    assertThat(restored.getRGB(10,10)).isEqualTo(new Color(20,180,70).getRGB());
    assertThat(service.get(asset.id()).contentType).isEqualTo("image/png");
  }

  @Test
  void dimensionsContentAndPlacementAreValidatedBeforeRendering() throws Exception {
    var service = new JourneyMediaService(repository, 2);
    assertThatThrownBy(() -> service.save(image(2401, 1, false)))
        .hasMessage("journey.media.invalid");
    assertThatThrownBy(
            () ->
                service.save(
                    "data:image/png;base64,"
                        + Base64.getEncoder().encodeToString("<svg/>".getBytes())))
        .hasMessage("journey.media.invalid");
    assertThatThrownBy(
            () ->
                JourneyBranding.parse(
                    "{\"logo\":{\"mediaId\":\"https://private\",\"x\":0,\"y\":0,\"scale\":0.2}}"))
        .hasMessage("journey.media.position");
    var id = service.save(image(20, 100, false)).id();
    var placement = new JourneyBranding.Placement(id, 1, 1, .6);
    var document = new ExportRequest();
    document.slides = List.of(new Slide(), new Slide());
    new JourneyBrandingLayout(service).apply(document, new JourneyBranding(placement, placement));
    assertThat(document.slides.get(0).elements).hasSize(2);
    assertThat(document.slides.get(1).elements).hasSize(1);
    for (var page : document.slides)
      for (var item : page.elements) {
        assertThat(item.x).isGreaterThanOrEqualTo(24);
        assertThat(item.y).isGreaterThanOrEqualTo(24);
        assertThat(item.x + item.width).isLessThanOrEqualTo(page.width - 23.99);
        assertThat(item.y + item.height).isLessThanOrEqualTo(page.height - 23.99);
      }
  }

  static String image(int width, int height, boolean alpha) throws Exception {
    var image =
        new BufferedImage(
            width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(new Color(20, 180, 70, alpha ? 128 : 255));
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    image.flush();
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
  }
}
