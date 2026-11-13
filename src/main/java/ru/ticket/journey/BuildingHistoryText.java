package ru.ticket.journey;

import java.util.*;

/** Neutral labels only: dates and architect names come from the imported dataset. */
final class BuildingHistoryText {
  private static final Map<String,List<String>> LABELS=Map.of(
      "ru",List.of("Год постройки","Архитектор","Стиль"),"en",List.of("Built","Architect","Style"),
      "zh",List.of("建造年份","建筑师","风格"),"pl",List.of("Rok budowy","Architekt","Styl"),
      "it",List.of("Anno di costruzione","Architetto","Stile"),"es",List.of("Año de construcción","Arquitecto","Estilo"),
      "nl",List.of("Bouwjaar","Architect","Stijl"),"fr",List.of("Construction","Architecte","Style"),
      "de",List.of("Baujahr","Architekt","Stil"));
  static String describe(BuildingHistoryService.Info p,String language){
    var labels=LABELS.getOrDefault(language,LABELS.get("en"));var parts=new ArrayList<String>();
    if(p.constructionYear()!=null)parts.add(labels.get(0)+": "+p.constructionYear());
    if(p.architect()!=null&&!p.architect().isBlank())parts.add(labels.get(1)+": "+p.architect());
    if(p.style()!=null&&!p.style().isBlank())parts.add(labels.get(2)+": "+p.style());
    return String.join(" · ",parts);
  }
}
