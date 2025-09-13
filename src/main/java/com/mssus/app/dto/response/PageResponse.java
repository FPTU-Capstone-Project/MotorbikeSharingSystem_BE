package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated response")
public class PageResponse<T> {

    @Schema(description = "Data items")
    private List<T> data;

    @Schema(description = "Pagination information")
    private PaginationInfo pagination;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Pagination metadata")
    public static class PaginationInfo {
        @Schema(description = "Current page number", example = "1")
        private Integer page;

        @JsonProperty("page_size")
        @Schema(description = "Number of items per page", example = "20")
        private Integer pageSize;

        @JsonProperty("total_pages")
        @Schema(description = "Total number of pages", example = "5")
        private Integer totalPages;

        @JsonProperty("total_records")
        @Schema(description = "Total number of records", example = "100")
        private Long totalRecords;
    }
}
