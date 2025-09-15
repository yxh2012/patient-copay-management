package com.yhou.demo.dto;

import com.yhou.demo.entity.enums.VisitType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing patient visit information with associated copays.
 */
@Data
public class VisitDTO {
    private Long id;
    private Long patientId;
    private LocalDate visitDate;
    private String doctorName;
    private String department;
    private VisitType visitType;
    private LocalDateTime createdAt;
    private List<CopayDTO> copays;
}
