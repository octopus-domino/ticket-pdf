package ru.ticket.journey.poetry;

/** Repairs only an identified legacy Wikisource margin-number shape, without rewriting snapshots. */
public final class PoetryDisplayText {
  private PoetryDisplayText() {}
  public static String of(PoetryCardsService.Card card){
    return of(card.poem(),card.sourceUrl());
  }
  public static String of(String text,String sourceUrl){
    if(sourceUrl==null||!sourceUrl.matches("https://[a-z]{2}\\.wikisource\\.org/.*"))return text;
    String[] lines=text.split("\n",-1);int verse=0;
    for(int i=0;i<lines.length;i++){
      if(lines[i].isBlank())continue;
      verse++;
      if(verse%5!=0)continue;
      // Removed margins were glued directly to a letter. "10 years" and dates remain intact.
      lines[i]=lines[i].replaceFirst("^([\\h]*)"+verse+"(?=\\p{L})","$1");
    }
    return String.join("\n",lines);
  }
}
