package com.greedy.festa.festival.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public class FestivalUpdateRequest {
    private JsonNode hostId;
    private String importKey;
    private String name;
    private JsonNode startDate;
    private JsonNode endDate;
    private String posterUrl;
    private String description;
    private String venueName;
    private String address;
    private JsonNode latitude;
    private JsonNode longitude;
    private JsonNode externalVisitor;
    private JsonNode verification;
    private JsonNode ticketType;
    private JsonNode ticketOpenAt;
    private String admissionNote;
    private String instagramUrl;
    private boolean hostIdPresent;
    private boolean importKeyPresent;
    private boolean namePresent;
    private boolean startDatePresent;
    private boolean endDatePresent;
    private boolean posterUrlPresent;
    private boolean descriptionPresent;
    private boolean venueNamePresent;
    private boolean addressPresent;
    private boolean admissionNotePresent;
    private boolean instagramUrlPresent;

    public JsonNode hostId() { return hostId; }
    public String importKey() { return importKey; }
    public String name() { return name; }
    public JsonNode startDate() { return startDate; }
    public JsonNode endDate() { return endDate; }
    public String posterUrl() { return posterUrl; }
    public String description() { return description; }
    public String venueName() { return venueName; }
    public String address() { return address; }
    public JsonNode latitude() { return latitude; }
    public JsonNode longitude() { return longitude; }
    public JsonNode externalVisitor() { return externalVisitor; }
    public JsonNode verification() { return verification; }
    public JsonNode ticketType() { return ticketType; }
    public JsonNode ticketOpenAt() { return ticketOpenAt; }
    public String admissionNote() { return admissionNote; }
    public String instagramUrl() { return instagramUrl; }

    @JsonIgnore @Schema(hidden = true) public boolean isHostIdPresent() { return hostIdPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isImportKeyPresent() { return importKeyPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isNamePresent() { return namePresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isStartDatePresent() { return startDatePresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isEndDatePresent() { return endDatePresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isPosterUrlPresent() { return posterUrlPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isDescriptionPresent() { return descriptionPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isVenueNamePresent() { return venueNamePresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isAddressPresent() { return addressPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isAdmissionNotePresent() { return admissionNotePresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isInstagramUrlPresent() { return instagramUrlPresent; }

    public void setHostId(JsonNode value) { hostId = value; hostIdPresent = true; }
    public void setImportKey(String value) { importKey = value; importKeyPresent = true; }
    public void setName(String value) { name = value; namePresent = true; }
    public void setStartDate(JsonNode value) { startDate = value; startDatePresent = true; }
    public void setEndDate(JsonNode value) { endDate = value; endDatePresent = true; }
    public void setPosterUrl(String value) { posterUrl = value; posterUrlPresent = true; }
    public void setDescription(String value) { description = value; descriptionPresent = true; }
    public void setVenueName(String value) { venueName = value; venueNamePresent = true; }
    public void setAddress(String value) { address = value; addressPresent = true; }
    public void setLatitude(JsonNode value) { latitude = value; }
    public void setLongitude(JsonNode value) { longitude = value; }
    public void setExternalVisitor(JsonNode value) { externalVisitor = value; }
    public void setVerification(JsonNode value) { verification = value; }
    public void setTicketType(JsonNode value) { ticketType = value; }
    public void setTicketOpenAt(JsonNode value) { ticketOpenAt = value; }
    public void setAdmissionNote(String value) { admissionNote = value; admissionNotePresent = true; }
    public void setInstagramUrl(String value) { instagramUrl = value; instagramUrlPresent = true; }
}
