package ru.ticket.generation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import ru.ticket.image.HttpImageEmbedder;
import ru.ticket.image.PublicImageDownloader;
import ru.ticket.pdf.PdfASvgToPdfConverter;
import ru.ticket.pdf.BatikSvgToPdfConverter;
import ru.ticket.svg.SvgSanitizer;

class EmbeddedImagePdfTest {
  @Test void drawioHtmlLabelsKeepAllLinesAndFormattingInPdfInsteadOfDisappearing() throws Exception {
    String raw="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"300\" height=\"150\"><rect width=\"300\" height=\"150\" fill=\"white\"/><g transform=\"translate(20,20)\"><switch><foreignObject width=\"100%\" height=\"100%\"><div xmlns=\"http://www.w3.org/1999/xhtml\" style=\"display:flex;align-items:unsafe center;justify-content:unsafe center;width:250px;height:1px;padding-top:50px;margin-left:1px\"><div style=\"font-size:0;text-align:center\"><div style=\"font-size:20px;font-family:Arial;line-height:1.2;color:light-dark(rgb(0,0,0),rgb(255,255,255))\"><b>FIRST LINE</b><br/>SECOND LINE<br/><i>THIRD LINE</i></div></div></div></foreignObject><text x=\"125\" y=\"50\">Text is not SVG - cannot display</text></switch></g></svg>";
    String svg=SvgSanitizer.sanitize(raw);
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/drawio-label-test.svg"),svg);
    assertFalse(svg.contains("foreignObject"));assertFalse(svg.contains("Text is not SVG"));
    assertTrue(svg.contains("FIRST"),"First line missing: "+svg);
    assertTrue(svg.contains("SECOND"),"Second line missing: "+svg);
    assertTrue(svg.contains("THIRD"),"Third line missing: "+svg);
    assertTrue(svg.contains("font-weight=\"700\""),"Bold style missing: "+svg);
    for(byte[] bytes:List.of(new PdfASvgToPdfConverter().convertMultiPage(List.of(svg,svg)),new BatikSvgToPdfConverter().convertMultiPage(List.of(svg))))try(var pdf=Loader.loadPDF(bytes)){
      var raster=new PDFRenderer(pdf).renderImage(0);int dark=0;for(int y=10;y<raster.getHeight()-10;y++)for(int x=10;x<raster.getWidth()-10;x++)if((raster.getRGB(x,y)&0xffffff)<0x777777)dark++;java.nio.file.Files.write(java.nio.file.Path.of("target/drawio-label-"+pdf.getNumberOfPages()+".pdf"),bytes);javax.imageio.ImageIO.write(raster,"png",java.nio.file.Path.of("target/drawio-label-"+pdf.getNumberOfPages()+".png").toFile());assertTrue(dark>400,"HTML label must visibly render in the PDF; pages="+pdf.getNumberOfPages()+", dark pixels="+dark);
    }
  }
  @Test void drawioThemeColorsRenderAsTheLightPrintedColors() throws Exception {
    String svg=SvgSanitizer.sanitize("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"80\"><rect width=\"100\" height=\"80\" style=\"fill:light-dark(rgb(218, 232, 252), rgb(29, 41, 59));stroke:light-dark(rgb(108, 142, 191), rgb(92, 121, 163));\"/><circle cx=\"10\" cy=\"10\" r=\"2\" fill=\"light-dark(rgb(255,242,204),rgb(40,29,0))\"/></svg>");
    assertFalse(svg.contains("light-dark("));assertTrue(svg.contains("rgb(218, 232, 252)"));
    for(byte[] bytes:List.of(new PdfASvgToPdfConverter().convertMultiPage(List.of(svg,svg)),new BatikSvgToPdfConverter().convertMultiPage(List.of(svg,svg))))try(var pdf=Loader.loadPDF(bytes)) {
      assertEquals(2,pdf.getNumberOfPages());var raster=new PDFRenderer(pdf).renderImage(0);assertEquals(0xdae8fc,raster.getRGB(raster.getWidth()/2,raster.getHeight()/2)&0xffffff);
    }
  }
  @Test void missingImageReportsAnActionableErrorInsteadOfBatikNull() {
    var embedder = new HttpImageEmbedder(1048576, mock(PublicImageDownloader.class));
    var error = assertThrows(IllegalArgumentException.class, () -> embedder.embed("<svg xmlns=\"http://www.w3.org/2000/svg\"><image width=\"10\" height=\"10\"/></svg>"));
    assertTrue(error.getMessage().contains("Image source is missing or unsupported"));
  }
  @Test
  void vectorLogoInStampOrImageRemainsVisibleAndSanitizedInPdfA() throws Exception {
    String logo = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\"><script>alert(1)</script><rect width=\"40\" height=\"40\" fill=\"#ff0000\"/></svg>";
    for (String source : List.of("data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(logo.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        "data:image/svg+xml;charset=utf-8," + java.net.URLEncoder.encode(logo, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))) {
      String markup = ru.ticket.svg.SvgSerializationUtil.imageTag(source, 0, 0, 100, 80, " preserveAspectRatio=\"none\"");
      String svg = SvgSanitizer.sanitize("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"80\">" + markup + "</svg>");
      assertFalse(svg.contains("<script"));
      try (var pdf = Loader.loadPDF(new PdfASvgToPdfConverter().convertMultiPage(List.of(svg)))) {
        var raster = new PDFRenderer(pdf).renderImage(0);
        assertEquals(0xff0000, raster.getRGB(raster.getWidth() / 2, raster.getHeight() / 2) & 0xffffff);
      }
    }
  }
  @Test
  void svg2EmbeddedImageRendersOnEveryPdfAPageWithoutNetworkAccess() throws Exception {
    var raster = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) raster.setRGB(x, y, 0xff0000);
    var png = new ByteArrayOutputStream();
    ImageIO.write(raster, "png", png);
    String source = "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
    String svg = SvgSanitizer.sanitize("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"80\">"
        + "<image href=\"" + source + "\" x=\"0\" y=\"0\" width=\"100\" height=\"80\"/></svg>");
    var converter = new PdfASvgToPdfConverter();
    // Reproduce the original Batik error before normalization.
    assertThrows(Exception.class, () -> converter.convertMultiPage(List.of(svg)));
    var downloader = mock(PublicImageDownloader.class);
    var embedder = new HttpImageEmbedder(1048576, downloader);
    String normalized = embedder.embed(svg);
    assertTrue(normalized.contains("xlink:href="));
    assertEquals(normalized, embedder.embed(normalized));
    verifyNoInteractions(downloader);
    for (byte[] bytes : List.of(converter.convertMultiPage(List.of(normalized, normalized)),
        new BatikSvgToPdfConverter().convertMultiPage(List.of(normalized, normalized)))) {
      try (var pdf = Loader.loadPDF(bytes)) {
        assertEquals(2, pdf.getNumberOfPages());
        var renderer = new PDFRenderer(pdf);
        for (int page = 0; page < 2; page++) {
          var image = renderer.renderImage(page);
          assertEquals(0xff0000, image.getRGB(image.getWidth() / 2, image.getHeight() / 2) & 0xffffff);
        }
      }
    }
  }
}
