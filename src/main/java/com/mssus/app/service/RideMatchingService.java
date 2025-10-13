package com.mssus.app.service;

import com.mssus.app.dto.LatLng;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.SharedRideRequest;

import java.util.List;
public interface RideMatchingService {

    /**
     * Find matching rides for an AI_BOOKING request.
     * 
     * <p><b>MVP Algorithm:</b></p>
     * <ol>
     *   <li><b>Filter Candidates:</b>
     *     <ul>
     *       <li>Ride status = SCHEDULED</li>
     *       <li>Available seats > 0</li>
     *       <li>Scheduled time within ±timeWindow of requested pickup</li>
     *     </ul>
     *   </li>
     *   <li><b>Proximity Check:</b>
     *     <ul>
     *       <li>Haversine distance from ride start/end to request pickup/dropoff</li>
     *       <li>Reject if distance > maxProximityKm</li>
     *     </ul>
     *   </li>
     *   <li><b>Detour Validation:</b> (TODO: Full corridor analysis)
     *     <ul>
     *       <li>Simple check: pickup/dropoff within bounding box of ride route</li>
     *       <li>Reject if detour > maxDetourKm or > driver's maxDetourMinutes</li>
     *     </ul>
     *   </li>
     *   <li><b>Scoring:</b>
     *     <ul>
     *       <li>Proximity score: (1 - distance/maxDistance) * weight</li>
     *       <li>Time alignment score: (1 - timeDelta/maxTimeDelta) * weight</li>
     *       <li>Driver rating score: (rating/5.0) * weight</li>
     *       <li>Detour penalty: (1 - detour/maxDetour) * weight</li>
     *       <li>Total score: sum of weighted scores</li>
     *     </ul>
     *   </li>
     *   <li><b>Sort & Limit:</b>
     *     <ul>
     *       <li>Sort by total score descending</li>
     *       <li>Return top N proposals (configurable, default 10)</li>
     *     </ul>
     *   </li>
     * </ol>
     * 
     * <p><b>TODO Future Enhancements:</b></p>
     * <ul>
     *   <li>Corridor buffering with PostGIS/JTS for accurate route alignment</li>
     *   <li>OSRM integration for real detour distance/time calculation</li>
     *   <li>ML-based scoring with historical acceptance rates</li>
     *   <li>Real-time traffic adjustments</li>
     *   <li>Multi-objective optimization (cost, time, comfort)</li>
     * </ul>
     * 
     * @param request the AI_BOOKING request to match
     * @return list of match proposals sorted by score (best first), empty if no matches
     */
    List<RideMatchProposalResponse> findMatches(SharedRideRequest request);


    double calculateDistance(double lat1, double lng1, double lat2, double lng2);
}

