package com.transitbike.mobile.data

import com.transitbike.shared.model.*
import com.transitbike.shared.nav.TransitV4ResponseMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RouteRepository(
    private val workerApi: WorkerProxyApi = WorkerProxyApi()
) {

    private val _favorites = MutableStateFlow<List<FavoritePlace>>(
        listOf(
            FavoritePlace("fav_home", "Home", "Midtown Atlanta, GA", LatLng(33.7816, -84.3831), "home"),
            FavoritePlace("fav_work", "Work", "Ponce City Market", LatLng(33.7725, -84.3656), "work"),
            FavoritePlace("fav_beltline", "BeltLine Trail", "Eastside Trail Access", LatLng(33.7710, -84.3630), "star")
        )
    )
    val favorites: StateFlow<List<FavoritePlace>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow<List<RecentRide>>(
        listOf(
            RecentRide("rec_1", "Ponce City Market", LatLng(33.7725, -84.3656), System.currentTimeMillis() - 86400000L, 4510.0, 840L),
            RecentRide("rec_2", "Piedmont Park", LatLng(33.7850, -84.3730), System.currentTimeMillis() - 172800000L, 2100.0, 420L)
        )
    )
    val recents: StateFlow<List<RecentRide>> = _recents.asStateFlow()

    suspend fun getRoutes(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): List<RouteModel> {
        val result = workerApi.planBikeRoute(fromLat, fromLon, toLat, toLon)
        return result.getOrElse {
            // Return sample Atlanta route fixture when network/backend is unavailable
            getSampleAtlantaRoutes()
        }
    }

    /**
     * High-fidelity built-in Atlanta bike route (10th St Cycletrack + BeltLine to Ponce City Market).
     */
    fun getSampleAtlantaRoutes(): List<RouteModel> {
        val fixtureJson = """
        {
          "results": [
            {
              "duration": 840,
              "legs": [
                {
                  "leg_mode": "personal_bike",
                  "duration": 840.0,
                  "distance": 4510.0,
                  "polyline": "g~_mE~fnaO_@mB_@mB_@mB_@mBr@aDr@aDlC_IlC_I",
                  "safety_analysis": {
                    "protected_lanes_percentage": 92.0,
                    "lanes_percentage": 96.5,
                    "safe_percentage": 98.0,
                    "segments": [
                      {
                        "way_name": "10th St NE Cycletrack",
                        "protection": "protected_lane",
                        "length": 965.0,
                        "polyline": "g~_mE~fnaO_@mB_@mB_@mB_@mB"
                      },
                      {
                        "way_name": "Monroe Dr NE",
                        "protection": "lane",
                        "length": 110.0,
                        "polyline": "o`anE~umaOr@aDr@aD"
                      },
                      {
                        "way_name": "Atlanta BeltLine Eastside Trail",
                        "protection": "protected_lane",
                        "length": 3360.0,
                        "polyline": "k~`nE~mmaOlC_I"
                      },
                      {
                        "way_name": "Ponce City Market Shed Ramp",
                        "protection": "protected_lane",
                        "length": 75.0,
                        "polyline": "ex_nE~dmaOlC_I"
                      }
                    ]
                  },
                  "directions": [
                    {
                      "length": 965.0,
                      "way_name": "10th St NE Cycletrack",
                      "duration": 180.0,
                      "angle": 90.0,
                      "instruction": "Head east on 10th St NE Cycletrack",
                      "polyline": "g~_mE~fnaO_@mB_@mB_@mB_@mB"
                    },
                    {
                      "length": 110.0,
                      "way_name": "Monroe Dr NE",
                      "duration": 25.0,
                      "angle": 88.0,
                      "instruction": "Turn right onto Monroe Dr NE",
                      "polyline": "o`anE~umaOr@aDr@aD"
                    },
                    {
                      "length": 3360.0,
                      "way_name": "Atlanta BeltLine Eastside Trail",
                      "duration": 615.0,
                      "angle": -92.0,
                      "instruction": "Turn left onto Atlanta BeltLine Eastside Trail",
                      "polyline": "k~`nE~mmaOlC_I"
                    },
                    {
                      "length": 75.0,
                      "way_name": "Ponce City Market Shed Ramp",
                      "duration": 20.0,
                      "angle": 85.0,
                      "instruction": "Turn right toward Ponce City Market Bike Valet",
                      "polyline": "ex_nE~dmaOlC_I"
                    }
                  ]
                }
              ]
            },
            {
              "duration": 660,
              "legs": [
                {
                  "leg_mode": "personal_bike",
                  "duration": 660.0,
                  "distance": 3380.0,
                  "polyline": "g~_mE~fnaOrC_@rC_@_@_I_@_I",
                  "safety_analysis": {
                    "protected_lanes_percentage": 18.0,
                    "lanes_percentage": 68.0,
                    "safe_percentage": 68.0,
                    "segments": [
                      {
                        "way_name": "Peachtree St NE",
                        "protection": "warning",
                        "length": 1120.0,
                        "polyline": "g~_mE~fnaOrC_@rC_@"
                      },
                      {
                        "way_name": "Ponce de Leon Ave NE",
                        "protection": "lane",
                        "length": 2260.0,
                        "polyline": "g|_mE~dnaO_@_I_@_I"
                      }
                    ]
                  },
                  "directions": [
                    {
                      "length": 1120.0,
                      "way_name": "Peachtree St NE",
                      "duration": 220.0,
                      "angle": 180.0,
                      "instruction": "Head south on Peachtree St NE",
                      "polyline": "g~_mE~fnaOrC_@rC_@"
                    },
                    {
                      "length": 2260.0,
                      "way_name": "Ponce de Leon Ave NE",
                      "duration": 440.0,
                      "angle": -90.0,
                      "instruction": "Turn left onto Ponce de Leon Ave NE",
                      "polyline": "g|_mE~dnaO_@_I_@_I"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()
        return TransitV4ResponseMapper.parseAndMap(fixtureJson)
    }
}
