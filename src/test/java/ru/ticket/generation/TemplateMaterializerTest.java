package ru.ticket.generation;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.dto.*;
import ru.ticket.generation.domain.TemplateMaterializer;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.schema.domain.FieldDef;

class TemplateMaterializerTest {
  @Test void repeatedConstructorPagesBindArrayChunksAndDoNotLeakPreviousPhotos() {
    var slide = new Slide(); slide.id = "album"; slide.repeat = new Slide.Repeat("album.photos", 2);
    var first = new TextElement(); first.type = "text"; first.id = "caption1"; first.text = "{{album.photos[0].caption}}";
    var second = new TextElement(); second.type = "text"; second.id = "caption2"; second.text = "{{album.photos[1].caption}}";
    slide.elements.addAll(List.of(first, second));
    var template = new GenerationCatalog.Template(List.of(slide), null,
        List.of(new FieldDef("album.photos", "array", false, "Photos", null)), List.of());
    var model = Map.<String,Object>of("album", Map.of("photos", List.of(Map.of("caption", "First"), Map.of("caption", "Second"), Map.of("caption", "Third"))));
    var pages = TemplateMaterializer.fill(template, model);
    assertThat(pages).hasSize(2);
    assertThat(((TextElement)pages.get(0).elements.get(1)).text).isEqualTo("Second");
    assertThat(((TextElement)pages.get(1).elements.get(0)).text).isEqualTo("Third");
    assertThat(((TextElement)pages.get(1).elements.get(1)).text).isEmpty();
    assertThat(pages.get(1).repeat).isNull();
    assertThat(slide.repeat).isNotNull();
    assertThat(first.text).contains("{{");
  }
}
