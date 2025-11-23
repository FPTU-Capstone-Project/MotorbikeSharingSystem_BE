//package com.mssus.app.config;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.graphhopper.GraphHopper;
//import com.graphhopper.GraphHopperConfig;
//import com.graphhopper.config.CHProfile;
//import com.graphhopper.config.LMProfile;
//import com.graphhopper.config.Profile;
//import com.graphhopper.jackson.Jackson;
//import com.graphhopper.util.CustomModel;
//import org.jetbrains.annotations.NotNull;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.Collections;
//
//@Configuration
//public class GraphHopperSpringConfig {
//    @Bean
//    public GraphHopper graphHopper() {
//        GraphHopperConfig ghConfig = getGraphHopperConfig();
//
//        // Optional: Enable CH/LM for faster queries (add if you want speed mode)
//        ghConfig.setCHProfiles(Collections.singletonList(new CHProfile("scooter")));
//        ghConfig.setLMProfiles(Collections.singletonList(new LMProfile("scooter")));
//
//        String customModelJson = """
//            {
//                "priority": [
//                    { "if": "road_access == DESTINATION", "multiply_by": "0" }\s
//                ],
//                "speed": [
//                    { "if": "true", "limit_to": "45" }\s
//                ]
//            }
//           \s""";
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        Jackson.initObjectMapper(objectMapper);
//        CustomModel customModel;
//        try {
//            JsonNode jsonNode = objectMapper.readTree(customModelJson);
//            customModel = objectMapper.convertValue(jsonNode, CustomModel.class);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to parse custom model JSON", e);
//        }
//
//
//        ghConfig.setProfiles(Collections.singletonList(
//            new Profile("scooter")
//                .setWeighting("custom")
//                .setCustomModel(customModel)
//        ));
//
//        GraphHopper hopper = new GraphHopper();
//        hopper.init(ghConfig);
//
//        hopper.importOrLoad();
//        return hopper;
//    }
//
//    @NotNull
//    private static GraphHopperConfig getGraphHopperConfig() {
//        GraphHopperConfig ghConfig = new GraphHopperConfig();
//
//        // Set basic properties using putObject (mimics config.yml keys)
//        ghConfig.putObject("datareader.file", "src/main/resources/vietnam-251001.osm.pbf");  // OSM file
//        ghConfig.putObject("graph.location", "graph-cache");  // Cache location
//
//        // Enable required encoded values (comma-separated; add more like "surface" if needed)
//        ghConfig.putObject("graph.encoded_values", "road_access");
//        ghConfig.putObject("import.osm.ignored_highways", "footway,construction,cycleway,path,pedestrian,steps");
//        ghConfig.putObject("proximity.search_radius", 5);
//        return ghConfig;
//    }
//}
