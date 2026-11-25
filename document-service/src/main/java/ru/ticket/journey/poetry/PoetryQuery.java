package ru.ticket.journey.poetry;

import java.util.List;

public record PoetryQuery(String language,String mood,String audience,String moodText,String seed,List<PoetryWeatherService.Day> days) {
  public int maxLines(){return audience.equals("child")?20:audience.equals("teen")?24:32;}
  public int maxCharacters(){return audience.equals("child")?800:audience.equals("teen")?1200:1600;}
  public String need(){
    if(mood.equals("funny"))return "smile";
    boolean cold=days.stream().anyMatch(day->day.temperature()<5||day.symbol().contains("rain")||day.symbol().contains("snow"));
    return cold?"comfort":"hope";
  }
}
