package ru.ticket.pdf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.PDFRenderer;
import static org.assertj.core.api.Assertions.*;

class PdfImageDeduplicatorTest {
  @Test void repeatedArtworkIsStoredOnceAndRenderedPixelsStayIdentical() throws Exception {
    var image=new BufferedImage(400,300,BufferedImage.TYPE_INT_RGB);var random=new Random(7);
    for(int y=0;y<300;y++)for(int x=0;x<400;x++)image.setRGB(x,y,random.nextInt());
    byte[] original;
    try(var document=new PDDocument()){
      for(int i=0;i<7;i++){
        var page=new PDPage(new PDRectangle(400,300));document.addPage(page);
        try(var content=new PDPageContentStream(document,page)){content.drawImage(LosslessFactory.createFromImage(document,image),0,0,400,300);}
      }
      var output=new ByteArrayOutputStream();document.save(output);original=output.toByteArray();
    }
    byte[] optimized=PdfImageDeduplicator.optimize(original);
    assertThat(optimized.length).isLessThan(original.length/3);
    try(var before=Loader.loadPDF(original);var after=Loader.loadPDF(optimized)){
      assertThat(after.getNumberOfPages()).isEqualTo(7);
      var a=new PDFRenderer(before).renderImage(0);var b=new PDFRenderer(after).renderImage(0);
      assertThat(b.getRGB(0,0,400,300,null,0,400)).isEqualTo(a.getRGB(0,0,400,300,null,0,400));
      var first=after.getPage(0).getResources();var last=after.getPage(6).getResources();
      assertThat(first.getXObject(first.getXObjectNames().iterator().next()).getCOSObject())
          .isSameAs(last.getXObject(last.getXObjectNames().iterator().next()).getCOSObject());
    }
  }
}
