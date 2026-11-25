package ru.ticket.walks.web;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import ru.ticket.walks.infrastructure.geocoding.AddressSearchService;

@RestController
@RequestMapping("/api/walks/v1/addresses")
public class WalkAddressesController {
    private final AddressSearchService addresses;

    public WalkAddressesController(AddressSearchService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public List<AddressSearchService.Address> addresses(@RequestParam String q,
            @RequestParam(defaultValue = "en") String language) {
        return addresses.search(q, language);
    }

    @GetMapping("/city")
    public AddressSearchService.City city(@RequestParam double longitude, @RequestParam double latitude,
            @RequestParam(defaultValue = "en") String language) {
        return addresses.cityAt(longitude, latitude, language);
    }
}
