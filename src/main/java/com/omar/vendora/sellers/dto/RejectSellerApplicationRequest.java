package com.omar.vendora.sellers.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RejectSellerApplicationRequest(
    @JsonAlias({"rejectionReason", "reason"})
    String reason
) {}
