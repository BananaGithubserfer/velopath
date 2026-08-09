package com.example.velopath.core.search

data class PlaceSearchResult(
    val title: String,
    val subtitle: String,
    val uri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
