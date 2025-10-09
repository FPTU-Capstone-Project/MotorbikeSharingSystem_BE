//package com.mssus.app.controller;
//
//import com.mssus.app.service.OsrmRoutingService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequiredArgsConstructor
//public class RouteController {
//    private final OsrmRoutingService osrmRoutingService;
//
//    @GetMapping("/route")
//    public OsrmRoutingService.RouteResponse getRoute(
//        @RequestParam double fromLat,
//        @RequestParam double fromLon,
//        @RequestParam double toLat,
//        @RequestParam double toLon) {
//        return osrmRoutingService.getRoute(fromLat, fromLon, toLat, toLon);
//    }
//}
