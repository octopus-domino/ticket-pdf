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
      // Removed margins were glued directly to a letter. "10 years" and dates remain intact.
      var marker=java.util.regex.Pattern.compile("^([\\h]*)([1-9][0-9]{0,2})(?=\\p{L})").matcher(lines[i]);
      if(marker.find()){
        int number=Integer.parseInt(marker.group(2));
        // Long printed verses can wrap into an extra physical line in legacy source HTML.
        if(number%5==0&&Math.abs(number-verse)<=2)lines[i]=marker.replaceFirst("$1");
      }
    }
    return String.join("\n",lines);
  }
}
