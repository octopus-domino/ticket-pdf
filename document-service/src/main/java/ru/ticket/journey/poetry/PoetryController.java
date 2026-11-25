package ru.ticket.journey.poetry;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/journeys/poetry")
public class PoetryController {
  private final PoetryWeatherService weather;
  private final PoetryCardsService cards;
  public PoetryController(PoetryWeatherService weather, PoetryCardsService cards) { this.weather = weather; this.cards = cards; }
  @GetMapping("/weather")
  public PoetryWeatherService.Forecast weather(@RequestParam double latitude, @RequestParam double longitude, @RequestParam String zone) {
    return weather.forecast(latitude, longitude, zone);
  }
  @PostMapping
  public PoetryCardsService.Prepared prepare(@RequestBody PoetryCardsService.Input input) { return cards.prepare(input); }
  @GetMapping("/{id}")
  public PoetryCardsService.Bundle get(@PathVariable String id) { return cards.get(id); }
}
