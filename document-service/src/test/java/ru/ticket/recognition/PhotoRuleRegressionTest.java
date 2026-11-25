package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class PhotoRuleRegressionTest {
    @Test void texturedLandscapeEdgesAreNotConvertedToRulesByThreeCoincidentalContrastPoints() {
        var image=new BufferedImage(600,200,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setColor(new Color(180,200,220));g.fillRect(0,0,600,200);
        g.setColor(new Color(50,65,80));g.fillRect(50,90,501,21);
        // Bright breaks around the old quarter-samples mimic highlights in a photograph.
        g.setColor(Color.WHITE);
        for(int x:new int[]{175,300,425}){g.fillRect(x-2,90,5,10);g.fillRect(x-2,101,5,10);}
        g.dispose();
        assertThat(RuleDetector.detect(image,false)).isEmpty();
        assertThat(RuleDetector.detect(image,true)).isEmpty();
    }
    @Test void genuinePrintedRuleStillSurvives() {
        var image=new BufferedImage(600,200,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,600,200);g.setColor(Color.BLACK);g.fillRect(50,100,500,2);g.dispose();
        assertThat(RuleDetector.detect(image,false)).hasSize(1);
    }
}
