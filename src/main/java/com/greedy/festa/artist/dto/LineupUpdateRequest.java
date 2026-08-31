package com.greedy.festa.artist.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public class LineupUpdateRequest {
    private JsonNode artistId;
    private JsonNode day;
    private JsonNode displayOrder;
    private boolean dayPresent;
    private boolean displayOrderPresent;

    public JsonNode artistId() { return artistId; }
    public JsonNode day() { return day; }
    public JsonNode displayOrder() { return displayOrder; }
    @JsonIgnore @Schema(hidden = true) public boolean isDayPresent() { return dayPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isDisplayOrderPresent() { return displayOrderPresent; }
    public void setArtistId(JsonNode value) { artistId = value; }
    public void setDay(JsonNode value) { day = value; dayPresent = true; }
    public void setDisplayOrder(JsonNode value) { displayOrder = value; displayOrderPresent = true; }
}
