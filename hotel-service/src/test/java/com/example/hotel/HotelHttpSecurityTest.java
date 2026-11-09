package com.example.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class HotelHttpSecurityTest {

    @Autowired private MockMvc mvc;

    @Test
    void anonymousCannotOpenHotelList() throws Exception {
        mvc.perform(get("/api/hotels")).andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCannotCreateRoom() throws Exception {
        mvc.perform(post("/api/rooms")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hotelId\":1,\"number\":\"NEW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUserCannotCallServiceMethod() throws Exception {
        String body = "{\"bookingId\":123,\"startDate\":\"2030-01-01\","
                + "\"endDate\":\"2030-01-02\",\"requestId\":\"test\"}";
        mvc.perform(post("/api/rooms/1/confirm-availability")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSeeRoomStatistics() throws Exception {
        mvc.perform(get("/api/rooms/stats")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
