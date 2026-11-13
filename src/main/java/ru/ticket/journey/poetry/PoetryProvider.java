package ru.ticket.journey.poetry;

import java.io.IOException;
import java.util.List;

public interface PoetryProvider {
  String name();
  List<PoetryWork> find(PoetryQuery query) throws IOException;
}
