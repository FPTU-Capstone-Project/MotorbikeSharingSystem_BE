# AI Integration Quick Reference

Quick reference card for AI features in the Motorbike Sharing System.

---

## Enable/Disable AI

```bash
# Enable AI
export AI_ENABLED=true
export AI_API_KEY=your_key_here

# Disable AI (fallback to base algorithm)
export AI_ENABLED=false
```

---

## API Endpoints

### Driver Recommendations
```bash
GET /api/v1/ai/driver/recommendations
Authorization: Bearer <DRIVER_TOKEN>
```

Returns: Optimal times and popular routes for drivers

### Rider Popular Routes
```bash
GET /api/v1/ai/rider/popular-routes?lat={lat}&lng={lng}
Authorization: Bearer <RIDER_TOKEN>
```

Returns: Popular campus routes and peak times

### Matching Insights
```bash
GET /api/v1/ai/matching/insights
Authorization: Bearer <ANY_TOKEN>
```

Returns: AI performance metrics and statistics

---

## Key Configuration

```properties
# Enable/Disable
app.ai.enabled=true

# Provider (xai or openai)
app.ai.provider=xai

# API Settings
app.ai.api-url=https://api.x.ai/v1/chat/completions
app.ai.model=grok-beta
app.ai.api-key=${AI_API_KEY}

# Behavior
app.ai.fallback-to-base-algorithm=true
app.ai.timeout-seconds=5
```

---

## How AI Works

1. **Matching**: Base algorithm → AI ranks → Return best match
2. **Pattern Analysis**: Daily at 3 AM, analyzes last 30 days
3. **Recommendations**: Based on cached pattern analysis
4. **Fallback**: Auto-fallback to base algorithm if AI fails

---

## Log Messages

**Success**:
```
INFO: Invoking AI matching algorithm
INFO: AI successfully ranked candidates
```

**Fallback**:
```
WARN: AI matching failed, falling back to base algorithm
```

**Pattern Analysis**:
```
INFO: Starting AI Pattern Analysis
INFO: Popular routes identified: 5
```

---

## Database Queries

**Check AI logs**:
```sql
SELECT * FROM ai_matching_logs 
ORDER BY created_at DESC LIMIT 10;
```

**AI Performance**:
```sql
SELECT 
  COUNT(*) as total,
  SUM(CASE WHEN success THEN 1 ELSE 0 END) as successful,
  AVG(processing_time_ms) as avg_time
FROM ai_matching_logs
WHERE created_at >= NOW() - INTERVAL '7 days';
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| AI not working | Check `AI_ENABLED=true` and `AI_API_KEY` set |
| Slow responses | Reduce `app.ai.timeout-seconds` |
| Too many API calls | Increase `app.ai.cache-duration-minutes` |
| Pattern analysis not running | Check schedule: `0 0 3 * * *` (3 AM daily) |

---

## Requirements Satisfied

✅ "AI algorithms to match riders with drivers" → `AiMatchingService`  
✅ "AI to optimize ride-matching" → Context-aware ranking  
✅ "AI shall analyze ride patterns" → `AiPatternAnalysisService`  
✅ "AI to suggest routes" → Recommendation APIs  
✅ "Recommend shared ride opportunities" → Popular routes endpoint

---

## Code Locations

- **AI Matching**: `src/main/java/com/mssus/app/service/ai/AiMatchingService.java`
- **Pattern Analysis**: `src/main/java/com/mssus/app/service/ai/AiPatternAnalysisService.java`
- **API Endpoints**: `src/main/java/com/mssus/app/controller/AiInsightsController.java`
- **Configuration**: `src/main/resources/application.properties` (lines 214-251)

---

## Demo Commands

```bash
# 1. Show AI is enabled
grep "app.ai" application.properties

# 2. Watch AI in action
tail -f logs/application.log | grep "AI"

# 3. Test matching (see logs update)
curl -X POST localhost:8080/api/v1/ride-requests \
  -H "Authorization: Bearer TOKEN" \
  -d '{"quoteId":123, ...}'

# 4. Check AI logs in database
psql -d dbname -c "SELECT * FROM ai_matching_logs LIMIT 5;"

# 5. Get recommendations
curl localhost:8080/api/v1/ai/driver/recommendations \
  -H "Authorization: Bearer TOKEN"
```

---

## Cost Estimate

- **xAI Grok**: ~$2.25/month for 1000 matches/day
- **OpenAI GPT-4o-mini**: ~$2.25/month for 1000 matches/day

Negligible for capstone project.

---

**Version**: 1.0  
**For**: Capstone Project Demo  
**Updated**: November 2025


