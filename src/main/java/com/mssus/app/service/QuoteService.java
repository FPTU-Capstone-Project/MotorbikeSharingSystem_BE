package com.mssus.app.service;

import com.mssus.app.dto.request.QuoteRequest;
import com.mssus.app.pricing.model.FareBreakdown;
import com.mssus.app.pricing.model.Quote;

import java.util.UUID;

public interface QuoteService {
    Quote generateQuote(QuoteRequest request, int userId);
    Quote getQuote(UUID quoteId);
}
