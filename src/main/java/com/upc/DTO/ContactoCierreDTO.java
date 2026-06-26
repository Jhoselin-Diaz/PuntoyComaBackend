package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactoCierreDTO {
    private Long id;
    private String btnText;
    private String number;
    private String message;
    private Boolean visible;
}
