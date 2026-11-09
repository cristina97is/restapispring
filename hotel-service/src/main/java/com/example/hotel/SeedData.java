package com.example.hotel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedData {

    @Bean
    CommandLineRunner addDemoHotels(HotelRepository hotels, RoomRepository rooms) {
        return args -> {
            if (hotels.count() > 0) {
                return;
            }
            Hotel first = hotels.save(new Hotel("North Hotel", "Красноярск, ул. Мира, 1"));
            Hotel second = hotels.save(new Hotel("River Hotel", "Красноярск, набережная, 5"));

            rooms.save(new Room(first, "101", true));
            rooms.save(new Room(first, "102", true));
            rooms.save(new Room(first, "103", true));
            rooms.save(new Room(second, "201", true));
            rooms.save(new Room(second, "202", true));
        };
    }
}
