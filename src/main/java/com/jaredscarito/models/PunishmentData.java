package com.jaredscarito.models;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class PunishmentData {
    private long pid;
    private long discordId;
    private Date datetime;
    private String ruleIdsBroken;
    private String lastKnownName;
    private String lastKnownAvatar;
    private PunishmentType punishmentType;
    private String punishmentLength;
    private String reason;
    private long punishedBy;
    private String punishedByLastKnownName;
    private String punishedByLastKnownAvatar;
    private boolean deleted;

    public PunishmentData() {}
}
