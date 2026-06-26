package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactoBloqueDTO {
    private String id;
    private String title;
    private String description;
    private String icon;
    private String btnText;
    private String btnLink;
    private Boolean visible;
}
