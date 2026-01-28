# Auto-Matching Algorithm with Merchant Validation and Overdue Bias

## Overview
This document describes the enhanced auto-matching algorithm that matches cleared transactions to forecast transactions using a scoring-based approach with merchant validation and overdue bias.

## The Problem
The previous algorithm matched transactions to forecast transactions using only chronological order and date proximity. This caused issues when:
- A transaction came late (e.g., Netflix budgeted for day 15, arrives on day 20)
- Another transaction was budgeted for the same day (e.g., Spotify budgeted for day 20)
- The algorithm would pick Spotify's forecast transaction because day 20 was closer to Spotify's planned date

The merchant information was completely ignored, leading to incorrect matches.

## The Solution

### Scoring-Based Matching
The new algorithm uses a scoring system that considers multiple factors:

#### 1. Merchant Matching (Primary Factor - 100 points)
- **+100 points**: Transaction merchant matches one of the budget item's merchants
- **-1000 points**: Transaction merchant doesn't match AND budget item has merchants (disqualifies)
- **0 points**: Budget item has no merchants (neutral, allows fallback to date matching)

This ensures that if merchant data is available, it's the primary determining factor.

#### 2. Date Proximity with Overdue Bias (Up to 50 points)
**Overdue/Late Transactions (BONUS points):**
- 0-3 days overdue: +50 points
- 4-7 days overdue: +40 points
- 8-14 days overdue: +30 points
- 15-30 days overdue: +20 points
- 31+ days overdue: +10 points

**On-time Transactions:**
- Exact date match: +30 points

**Early Transactions:**
- 1-3 days early: +20 points
- 4-7 days early: +10 points
- 8-14 days early: +5 points
- 15+ days early: 0 points

**Future Transactions:**
- Future dates: 0 points (shouldn't match yet)

**Rationale:** Late payments are common (bills arrive late, auto-payments process late, etc.). Early payments are less common. This bias reflects real-world payment behavior.

#### 3. Amount Matching (Secondary Factor - Up to 20 points)
- Exact amount: +20 points
- Within 10%: +10 points
- Within 25%: +5 points
- Beyond 25%: 0 points

### Algorithm Flow

1. **Scoring Phase:**
   - Get ALL non-zero forecast transactions for the budget item
   - Calculate score for each candidate forecast transaction
   - Select the candidate with the highest score

2. **Validation Phase:**
   - If best score > 0 and match is found:
     - Check if transaction falls within the forecast transaction's window
     - For high-confidence matches (score >= 100 or within normal variance):
       - Auto-assign without user interaction
     - For lower-confidence matches:
       - Fall through to interactive validation

3. **Fallback Phase:**
   - If no good match found (score <= 0):
     - Use the original sequential date-based matching logic
     - Maintains backward compatibility

## Examples

### Example 1: Netflix Arriving Late
- Budget Item: Netflix, $15, planned for 15th
- Candidate 1: Netflix forecast transaction (15th) - Score: 100 (merchant) + 50 (5 days late) + 20 (exact amount) = **170**
- Candidate 2: Spotify forecast transaction (20th) - Score: -1000 (merchant mismatch) = **-1000**
- **Result:** Netflix forecast transaction is selected (correct!)

### Example 2: No Merchant Data
- Budget Item: Generic grocery, $100, planned for 1st
- Transaction arrives on 5th
- Forecast 1 (1st): Score: 0 (no merchant) + 50 (4 days late) + 20 (exact) = **70**
- Forecast 2 (10th): Score: 0 (no merchant) + 20 (5 days early) + 20 (exact) = **40**
- **Result:** Forecast 1 selected (overdue bias works!)

### Example 3: Budget Item with No Merchants
- Budget Item has no merchants assigned
- Both transaction and forecast transaction lack merchant data
- Algorithm falls back to date + amount scoring
- No penalty for missing merchant data

## Benefits

1. **Prevents Wrong Merchant Matches:** Merchant validation ensures Netflix transactions don't match Spotify forecast items
2. **Handles Late Payments Better:** Overdue bias recognizes that late payments are more common than early ones
3. **Reduces User Prompts:** High-confidence matches auto-assign, reducing manual intervention
4. **Backward Compatible:** Falls back to original logic when merchant data isn't available
5. **Handles Multiple Scenarios:** Works with or without merchant data, handles various date variances

## Implementation Details

### Location
`ForecastTransactionController.java` - `getApplicableForecastTransaction()` method

### Key Methods
- `calculateMatchScore()`: Computes the match score for a candidate forecast transaction
- `getApplicableForecastTransaction()`: Main matching logic with scoring and fallback

### Database Dependencies
- `BudgetItemMerchant.getAssignedMerchantsForBudgetItem()`: Gets merchants for a budget item
- `Transaction.getMerchant()`: Gets the merchant for a transaction

## Testing Recommendations

1. Test with transactions that have merchants vs. those that don't
2. Test with late payments (should favor overdue forecast transactions)
3. Test with early payments (should still work but with lower scores)
4. Test with budget items that have no merchants (should fall back gracefully)
5. Test with exact date matches vs. variance

## Future Enhancements

1. **Learning System:** Track user corrections to adjust scoring weights
2. **Category Matching:** Add scoring for category matches
3. **Pattern Recognition:** Recognize recurring transaction patterns
4. **Confidence Display:** Show match confidence to user when asking for confirmation
5. **Tunable Weights:** Make scoring weights configurable per user preference
